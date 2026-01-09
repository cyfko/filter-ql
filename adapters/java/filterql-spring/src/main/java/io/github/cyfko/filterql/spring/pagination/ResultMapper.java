package io.github.cyfko.filterql.spring.pagination;

import java.util.Map;


public interface ResultMapper<R> {
    R map(Map<String,Object> item);
}
