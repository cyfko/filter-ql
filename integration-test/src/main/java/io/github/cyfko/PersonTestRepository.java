package io.github.cyfko;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PersonTestRepository extends JpaRepository<PersonTest, Long>, JpaSpecificationExecutor<PersonTest> {}