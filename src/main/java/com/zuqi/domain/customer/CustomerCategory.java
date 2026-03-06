package com.zuqi.domain.customer;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "customer_categories")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;
}
