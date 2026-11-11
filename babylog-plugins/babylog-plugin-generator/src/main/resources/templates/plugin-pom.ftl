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

    <artifactId>babylog-plugin-${pluginName}</artifactId>
    <name>babylog-plugin-${pluginName}</name>
    <description>BabyLog ${displayName}插件实现层：${moduleDesc}——AI Coding 骨架生成</description>

    <dependencies>
        <!-- 本插件契约 -->
        <dependency>
            <groupId>com.babylog</groupId>
            <artifactId>babylog-plugin-${pluginName}-api</artifactId>
        </dependency>

        <!-- 底座 Starter（按需增删，跨 Plugin 只许依赖 -api 包） -->
        <dependency>
            <groupId>com.babylog</groupId>
            <artifactId>babylog-starter-core</artifactId>
        </dependency>
        <dependency>
            <groupId>com.babylog</groupId>
            <artifactId>babylog-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>com.babylog</groupId>
            <artifactId>babylog-starter-mybatis</artifactId>
        </dependency>
        <dependency>
            <groupId>com.babylog</groupId>
            <artifactId>babylog-starter-auth</artifactId>
        </dependency>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
