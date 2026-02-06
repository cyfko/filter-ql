package io.github.cyfko.filterql.spring.controller;

import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.filterql.core.api.PropertyReference;
import io.github.cyfko.filterql.spring.service.FilterQlService;
import io.github.cyfko.filterql.spring.pagination.PaginatedData;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.Map;
import javax.annotation.processing.Generated;

${annotationsImports}

@Generated("io.github.cyfko.filterql.spring.processor.ExposureAnnotationProcessor")
@RestController
public class FilterQlController {
    private FilterQlService searchService;

    public FilterQlController(FilterQlService filterQLService) {
        this.searchService = filterQLService;
    }

    ${searchEndpoints}
}