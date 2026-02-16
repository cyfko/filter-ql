package io.github.cyfko;


import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.helper.AddressDTOImpl;
import io.github.cyfko.helper.PersonDTOImpl;
import io.github.cyfko.projection.Projection;
import io.github.cyfko.projection.Provider;
import io.github.cyfko.filterql.spring.ExposedAs;
import io.github.cyfko.filterql.spring.Exposure;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Projection(
        from = Person.class,
        providers = {
                @Provider(VirtualFields.class),
                @Provider(VirtualFieldsExtension.class)
        }
)
@Exposure(
        value = "users",
        basePath = "/api/v1"
)
@JsonDeserialize(as = PersonDTOImpl.class) // Only for mapping JSON -> Java, for tests purposes
public interface PersonDTO {

    Long getId();

    @ExposedAs(value = "USERNAME", operators = {Op.EQ, Op.MATCHES, Op.NE, Op.IN})
    String getUsername();

    @ExposedAs(value = "EMAIL", operators = {Op.EQ, Op.MATCHES, Op.NE})
    String getEmail();

    @ExposedAs(value = "FIRST_NAME", operators = {Op.EQ, Op.MATCHES, Op.IN})
    String getFirstName();

    @ExposedAs(value = "LAST_NAME", operators = {Op.EQ, Op.MATCHES, Op.IN, Op.IS_NULL})
    String getLastName();

    @ExposedAs(value = "AGE", operators = {Op.EQ, Op.GT, Op.LT, Op.GTE, Op.LTE, Op.RANGE})
    Integer getAge();

    @ExposedAs(value = "ACTIVE", operators = {Op.EQ})
    Boolean isActive();

    @ExposedAs(value = "REGISTERED_AT", operators = {Op.EQ, Op.GT, Op.LT, Op.GTE, Op.LTE, Op.RANGE})
    LocalDateTime getRegisteredAt();

    @ExposedAs(value = "BIRTH_DATE", operators = {Op.EQ, Op.GT, Op.LT, Op.GTE, Op.LTE, Op.RANGE})
    LocalDate getBirthDate();
}
