package io.github.cyfko.filterql.tests.entities.projection._1;

import jakarta.persistence.*;

@Entity
@Table(name = "test_projection_users")
public class UserB {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;
    private boolean active;
    private String phone;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "address_id")
    private AddressB address;

    public UserB() {}

    public UserB(String name, String email, boolean active, String phone, AddressB address) {
        this.name = name;
        this.email = email;
        this.active = active;
        this.phone = phone;
        this.address = address;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public AddressB getAddress() { return address; }
    public void setAddress(AddressB address) { this.address = address; }
}