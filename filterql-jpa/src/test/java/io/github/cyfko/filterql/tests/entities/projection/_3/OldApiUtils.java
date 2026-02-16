package io.github.cyfko.filterql.tests.entities.projection._3;

import java.util.List;

public class OldApiUtils {
    public static List<Object> toKeyIdentifier(Long id, String name){
        return List.of(id,name);
    }

    public static DtoUserD.History toLastHistory(Long id){
        return null;
    }

    public static String keyAsString(List<Object> parts){
        StringBuilder result = new StringBuilder();
        for(Object part : parts){
            result.append(part).append("-");
        }
        result.deleteCharAt(result.length()-1);
        return result.toString();
    }
}
