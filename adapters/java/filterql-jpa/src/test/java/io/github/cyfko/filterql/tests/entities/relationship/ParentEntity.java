package io.github.cyfko.filterql.tests.entities.relationship;

import io.github.cyfko.filterql.tests.entities.ecommerce.Address;
import io.github.cyfko.filterql.tests.entities.ecommerce.User;
import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "test_parent_entity")
public class ParentEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
    
    @OneToMany(mappedBy = "entity")
    @OrderBy("name ASC")
    private List<ChildEntity> children;
    
    @Embedded
    private Address address;
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public User getUser() {
        return user;
    }
    
    public void setUser(User user) {
        this.user = user;
    }
    
    public List<ChildEntity> getChildren() {
        return children;
    }
    
    public void setChildren(List<ChildEntity> children) {
        this.children = children;
    }
    
    public Address getAddress() {
        return address;
    }
    
    public void setAddress(Address address) {
        this.address = address;
    }
}