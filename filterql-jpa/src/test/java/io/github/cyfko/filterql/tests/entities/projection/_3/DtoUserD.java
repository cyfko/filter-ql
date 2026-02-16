package io.github.cyfko.filterql.tests.entities.projection._3;

import io.github.cyfko.projection.*;

import java.util.List;

@Projection(from = UserD.class, providers = @Provider(OldApiUtils.class))
public interface DtoUserD {

    @Projected(from = "id")
    Long getId();

    @Projected(from = "name")
    String getName();

    @Projected(from = "orders")
    List<DtoOrderD> getOrders();

    @Computed(dependsOn = {"id", "name"})
    String getKeyIdentifier(); // Old API identifier

    @Computed(dependsOn = {"id"})
    History getLastHistory();

    class History {
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
