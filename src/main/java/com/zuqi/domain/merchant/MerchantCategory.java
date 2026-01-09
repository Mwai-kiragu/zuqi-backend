package com.zuqi.domain.merchant;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "merchant_categories")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;
}
