package io.github.cyfko.filterql.tests.entities.projection._3;

public class OldApiUtils {
    public static String getKeyIdentifier(Long id, String name){
        return id.toString() + "-" + name;
    }

    public static DtoUserD.History getLastHistory(Long id){
        return null;
    }
}
