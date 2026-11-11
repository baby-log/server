<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.babylog</groupId>
        <artifactId>babylog-plugins</artifactId>
        <version>${projectVersion}</version>
    </parent>

    <artifactId>babylog-plugin-${pluginName}-api</artifactId>
    <name>babylog-plugin-${pluginName}-api</name>
    <description>BabyLog ${displayName}插件契约层（Facade 接口 / DTO / ${errorCodeBase?c}xx 错误码，禁框架依赖）——AI Coding 骨架生成</description>

    <dependencies>
        <dependency>
            <groupId>com.babylog</groupId>
            <artifactId>babylog-common-core</artifactId>
        </dependency>
        <dependency>
            <groupId>com.babylog</groupId>
            <artifactId>babylog-common-api</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
