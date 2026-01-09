package io.github.cyfko.filterql.spring.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import java.io.IOException;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ExposureAnnotationProcessorIntegrationTest {

    @Test
    void testBothProjectionsGenerated() throws IOException {
        getJavaFileObjects();

        Compilation compilation = Compiler.javac()
                .withProcessors(new ExposureAnnotationProcessor())
                .compile(getJavaFileObjects());

        assertThat(compilation).succeeded();

        String generatedCode = getGeneratedControllerCode(compilation);

        // Verify both DTOs are in the generated registry
        assertTrue(generatedCode.contains("import org.springframework.security.access.prepost.PreAuthorize;"));
        assertTrue(generatedCode.contains("import org.springframework.cache.annotation.Cacheable;"));
        assertTrue(generatedCode.contains("@PreAuthorize(\"hasAuthority('USER')\")"));
        assertTrue(generatedCode.contains("@Cacheable(value = \"userSearchCache\", key = \"#filterRequest.hashCode()\")"));
        assertTrue(generatedCode.contains("@PreAuthorize(\"hasAuthority('ADMIN')\")"));
        assertTrue(generatedCode.contains("return searchService.search(io.github.cyfko.example.PersonDTO_.class, req);"));
        assertTrue(generatedCode.contains("return searchService.search(io.github.cyfko.example.AddressDTO_.class, req);"));
    }

    private static JavaFileObject[] getJavaFileObjects() {
        return new  JavaFileObject[] {
                JavaFileObjects.forResource("testdata/Person.java"),
                JavaFileObjects.forResource("testdata/PersonDTO.java"),
                JavaFileObjects.forResource("testdata/Address.java"),
                JavaFileObjects.forResource("testdata/AddressDTO.java"),
                JavaFileObjects.forResource("testdata/UserTenancyService.java"),
                JavaFileObjects.forResource("testdata/VirtualResolverConfig.java")
        };
    }

    private String getGeneratedControllerCode(Compilation compilation) throws IOException {
        return compilation
                .generatedSourceFile("io.github.cyfko.filterql.spring.controller.FilterQlController")
                .orElseThrow(() -> new AssertionError("Generated projection provider not found"))
                .getCharContent(true)
                .toString();
    }
}
