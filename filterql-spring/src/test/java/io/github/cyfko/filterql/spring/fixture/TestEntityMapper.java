package io.github.cyfko.filterql.spring.fixture;

/**
 * Mapper for converting TestEntity to TestEntityDTO.
 * <p>
 * Uses the standard 'map' method naming convention.
 * </p>
 */
public class TestEntityMapper {

    /**
     * Maps TestEntity to TestEntityDTO.
     *
     * @param entity the entity to map
     * @return the DTO
     */
    public static TestEntityDTO map(TestEntity entity) {
        if (entity == null) {
            return null;
        }

        return new TestEntityDTO(
            entity.getId(),
            entity.getName(),
            entity.getAge(),
            entity.getActive()
        );
    }
}
