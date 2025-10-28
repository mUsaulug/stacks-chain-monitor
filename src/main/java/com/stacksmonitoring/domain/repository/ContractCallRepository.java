package com.stacksmonitoring.domain.repository;

import com.stacksmonitoring.domain.model.blockchain.ContractCall;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for ContractCall entities.
 */
@Repository
public interface ContractCallRepository extends JpaRepository<ContractCall, Long> {

    @Query("SELECT c FROM ContractCall c WHERE c.contractIdentifier = :contractIdentifier ORDER BY c.transaction.createdAt DESC")
    Page<ContractCall> findByContractIdentifier(@Param("contractIdentifier") String contractIdentifier, Pageable pageable);

    @Query("SELECT c FROM ContractCall c WHERE c.functionName = :functionName ORDER BY c.transaction.createdAt DESC")
    Page<ContractCall> findByFunctionName(@Param("functionName") String functionName, Pageable pageable);

    @Query("SELECT c FROM ContractCall c WHERE c.contractIdentifier = :contractIdentifier AND c.functionName = :functionName ORDER BY c.transaction.createdAt DESC")
    Page<ContractCall> findByContractIdentifierAndFunctionName(
        @Param("contractIdentifier") String contractIdentifier,
        @Param("functionName") String functionName,
        Pageable pageable
    );

    @Query("SELECT c FROM ContractCall c WHERE c.transaction.id = :transactionId")
    List<ContractCall> findByTransactionId(@Param("transactionId") Long transactionId);
}
