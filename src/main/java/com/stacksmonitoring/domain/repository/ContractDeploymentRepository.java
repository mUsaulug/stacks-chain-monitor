package com.stacksmonitoring.domain.repository;

import com.stacksmonitoring.domain.model.blockchain.ContractDeployment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ContractDeployment entities.
 */
@Repository
public interface ContractDeploymentRepository extends JpaRepository<ContractDeployment, Long> {

    Optional<ContractDeployment> findByContractIdentifier(String contractIdentifier);

    boolean existsByContractIdentifier(String contractIdentifier);

    @Query("SELECT d FROM ContractDeployment d WHERE d.traitImplementations LIKE %:trait% ORDER BY d.transaction.createdAt DESC")
    List<ContractDeployment> findByTraitImplementation(@Param("trait") String trait);

    @Query("SELECT d FROM ContractDeployment d WHERE d.contractName = :contractName ORDER BY d.transaction.createdAt DESC")
    List<ContractDeployment> findByContractName(@Param("contractName") String contractName);
}
