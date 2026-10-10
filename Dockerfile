# 1. 构建：在容器内编译，产物不受本地环境影响
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
# 先只拷 pom 下依赖，源码变动时这一层能命中缓存
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# 2. 分层：拆成 dependencies / loader / snapshot / application 四层
#    日常改代码只影响 application 层，镜像推送和拉取都只剩一小层
FROM eclipse-temurin:25-jre AS layers
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
# 少了 --launcher 会得到一个空的 spring-boot-loader 目录，容器起不来
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# 3. 运行
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=layers /app/extracted/dependencies/ ./
COPY --from=layers /app/extracted/spring-boot-loader/ ./
COPY --from=layers /app/extracted/snapshot-dependencies/ ./
COPY --from=layers /app/extracted/application/ ./

# 容器默认是 UTC，会把 LocalDateTime 按 UTC 落库，与应用的 Asia/Shanghai 差 8 小时
ENV TZ=Asia/Shanghai
EXPOSE 8000
ENTRYPOINT ["java", "-Duser.timezone=Asia/Shanghai", "org.springframework.boot.loader.launch.JarLauncher"]
