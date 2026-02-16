package io.github.cyfko;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.helper.AddressDTOImpl;
import io.github.cyfko.projection.Projection;
import io.github.cyfko.filterql.spring.ExposedAs;
import io.github.cyfko.filterql.spring.Exposure;
import io.github.cyfko.projection.Provider;

/**
 * DTO for Address entity.
 */
@Projection(
        from = Address.class,
        providers = @Provider(VirtualFields.class)
)
@Exposure(
        value = "addresses",
        basePath = "/api/v1"
)
@JsonDeserialize(as = AddressDTOImpl.class) // Only for mapping JSON -> Java, for tests purposes
public interface AddressDTO {
    Long getId();

    @ExposedAs(value = "STREET", operators = {Op.EQ, Op.MATCHES, Op.NE})
    String getStreet();

    @ExposedAs(value = "CITY", operators = {Op.EQ, Op.MATCHES, Op.IN})
    String getCity();

    @ExposedAs(value = "ZIP_CODE", operators = {Op.EQ, Op.MATCHES})
    String getZipCode();

    @ExposedAs(value = "COUNTRY", operators = {Op.EQ, Op.MATCHES, Op.IN})
    String getCountry();
}
