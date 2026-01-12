package io.github.cyfko.filterql.jpa.entities.projection._3;

import io.github.cyfko.projection.*;

import java.util.List;

@Projection(from = UserD.class, providers = @Provider(OldApiUtils.class))
public class DtoUserD {

    @Projected(from = "id")
    private Long id;

    @Projected(from = "name")
    private String name;

    @Projected(from = "orders")
    private List<DtoOrderD> orders;

    @Computed(dependsOn = {"id", "name"})
    private String keyIdentifier; // Old API identifier

    @Computed(dependsOn = {"id"})
    private History lastHistory;

    public List<DtoOrderD> getOrders() {
        return orders;
    }

    public void setOrders(List<DtoOrderD> orders) {
        this.orders = orders;
    }

    public String getKeyIdentifier() {
        return keyIdentifier;
    }

    public static class History {
        private String year;
        private String[] comments;

        public History(String year, String[] comments) {
            this.year = year;
            this.comments = comments;
        }

        public String getYear() {
            return year;
        }

        public void setYear(String year) {
            this.year = year;
        }

        public String[] getComments() {
            return comments;
        }

        public void setComments(String[] comments) {
            this.comments = comments;
        }
    }
}
