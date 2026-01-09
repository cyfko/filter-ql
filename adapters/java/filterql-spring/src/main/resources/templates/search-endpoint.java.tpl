${annotationDecorators}
    @PostMapping("${basePath}/search/${exposedName}")
    public PaginatedData<${listItemType}> ${methodName}(@RequestBody FilterRequest<${fqEnumName}> req) {
        return searchService.search(${fqEnumName}.class, req);
    }

