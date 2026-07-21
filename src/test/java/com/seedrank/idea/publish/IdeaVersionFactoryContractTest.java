package com.seedrank.idea.publish;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class IdeaVersionFactoryContractTest {

    @Test
    void snapshotFactoryNameExpressesThatItCreatesTheInitialVersion() {
        var factoryNames = Arrays.stream(IdeaVersion.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isStatic(method.getModifiers()))
                .map(java.lang.reflect.Method::getName)
                .toList();

        assertThat(factoryNames)
                .contains("initialSnapshot")
                .doesNotContain("first");
    }
}
