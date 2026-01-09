@Bean
public JpaFilterContext<${propertyRefEnumName}> ${beanName}(${contextParam}) {
    return new JpaFilterContext<>(${propertyRefEnumName}.class, pr -> switch (pr) {
${switchCases}
        }
    );
}