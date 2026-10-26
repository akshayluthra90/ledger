package com.akshayluthra.ledger.dao;

import com.akshayluthra.ledger.exception.DuplicateAccountException;
import com.akshayluthra.ledger.model.EntryType;
import com.akshayluthra.ledger.model.LedgerEntry;

import java.math.BigDecimal;

/**
 * Interface for storage which can be implemented to access different storage.
 */
public interface LedgerStorage {
    /**
     * Function to fetch a particular transaction given you have the transaction id.
     * @param transactionId : The id of the transaction that you are trying to fetch.
     * @return the {@link LedgerEntry}
     */
    public LedgerEntry getTransaction(String transactionId);

    /**
     * Function to add record to the storage for an account. This record can either be a debit or a credit.
     * @param ledgerEntry : Provides the ledger entry details including all the ledger entry details.
     * @param  debit : Tells you if this is a debit transaction or a credit. True : Is for debit/ false : Credit
     * @return transactionId
     */
    public String addRecord(LedgerEntry ledgerEntry, EntryType entryType);


    /**
     * Function to get the balance for the account associated with the accountId.
     * @param accountId : The accountId for the account for which the balance needs to be fetched.
     * @return : Balance of the account.
     */
    public BigDecimal getBalance(String accountId);

    /**
     * Function to add a new account. We need to make sure the account Id passed is unique, only then a new account
     * can be created, else an error {@link DuplicateAccountException} is thrown.
     * @param accountId : Account id of the account one want to create.
     * @return : The accountId if created successfully.
     */
    public String addAccount(String accountId);

    /**
     * Function to delete an account. Use this function carefully as any deleted account will not be able to perform
     * any transaction.
     * @param accountId : For account which one wants to delete.
     * @return : AccountId for the account deleted.
     */
    public String deleteAccount(String accountId);
}
