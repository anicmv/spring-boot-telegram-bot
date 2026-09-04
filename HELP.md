# Getting Started

### Reference Documentation

For further reference, please consider the following sections:

* [Official Gradle documentation](https://docs.gradle.org/current/userguide/userguide.html)
* [Spring Boot Gradle Plugin Reference Guide](https://docs.spring.io/spring-boot/4.1/gradle-plugin/)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.1/gradle-plugin/packaging-oci-image.html)
* [GraalVM Native Image Support](https://docs.spring.io/spring-boot/4.1/reference/packaging/native-image/introducing-graalvm-native-images.html)

### Additional Links

These additional references should also help you:

* [Configure AOT settings in Build Plugin](https://docs.spring.io/spring-boot/4.1/how-to/aot.html)

## GraalVM Native Support

This project has been configured to let you generate either a lightweight container or a native executable.
It is also possible to run your tests in a native image.

### Lightweight Container with Cloud Native Buildpacks

If you're already familiar with Spring Boot container images support, this is the easiest way to get started.
Docker should be installed and configured on your machine prior to creating the image.

To create the image, run the following task:

```
$ ./gradlew bootBuildImage
```

Then, you can run the app like any other container:

```
$ docker run --rm spring-boot-telegram-bot:0.0.1-SNAPSHOT
```

### Executable with Native Build Tools

Use this option if you want to explore more options such as running your tests in a native image.
The GraalVM `native-image` compiler should be installed and configured on your machine.

NOTE: GraalVM 25+ is required.

To create the executable, run the following task:

```
$ ./gradlew nativeCompile
```

Then, you can run the app as follows:

```
$ build/native/nativeCompile/spring-boot-telegram-bot
```

You can also run your existing test suite in a native image.
This is an efficient way to validate the compatibility of your application.

To run your existing tests in a native image, run the following task:

```
$ ./gradlew nativeTest
```
