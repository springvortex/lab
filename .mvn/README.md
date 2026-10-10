Maven 构建 JVM 参数（随项目走，Maven 启动时自动读取，无需额外配置）。

## 为什么需要这一行

Lombok 的 `lombok.permit.Permit` 在编译期通过 `sun.misc.Unsafe.objectFieldOffset(Field)`
绕过模块封装、改写 ClassLoader 内部字段，以便把自己注入编译器类加载器。
JDK 23 起该方法被标记为 terminally deprecated，每次编译都会刷出 4 行 WARNING：

    WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
    WARNING: sun.misc.Unsafe::objectFieldOffset has been called by lombok.permit.Permit
    WARNING: Please consider reporting this to the maintainers of class lombok.permit.Permit
    WARNING: sun.misc.Unsafe::objectFieldOffset will be removed in a future release

这不是项目代码的问题（与运行期无关，Lombok 是 optional 依赖、不打进 jar），
只是构建日志噪音。`--sun-misc-unsafe-memory-access=allow` 显式声明「已知晓并允许」，
即可消除这 4 行警告。构建结果不受影响（Lombok 生成的 getter/setter/Builder 照常工作）。

## ⚠️ 关键：这个参数不能照抄到其他分支

`--sun-misc-unsafe-memory-access=` 是 **JDK 23 才引入**的选项。
在更低的 JDK 上（8 / 11 / 21）会直接报错并终止 JVM：

    Unrecognized option: --sun-misc-unsafe-memory-access=allow
    Error: Could not create the Java Virtual Machine.

因此本文件 **只适用于 JDK 23+ 的分支**（当前仓库中是 `template` 与 `sample/boot4-jdk25`）。
若把它们合并/同步到下列分支，必须删掉本文件，否则 `mvn` 完全无法启动：

    sample/boot3-jdk21   →  JDK 21（不认识该参数）
    sample/boot2-jdk11   →  JDK 11（不认识该参数）
    sample/boot2-jdk8    →  JDK 8 （不认识该参数）

那三个分支上的同类警告属于上游依赖噪音，忽略即可，或用 `MAVEN_OPTS` 在个人环境变量里
按各自 JDK 支持的参数单独处理（不要提交到仓库）。
