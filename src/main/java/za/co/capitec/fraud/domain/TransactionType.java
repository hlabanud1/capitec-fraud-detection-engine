package za.co.capitec.fraud.domain;

/**
 * Banking transaction types based on payment rails and operations.
 */
public enum TransactionType {

    INTERNAL_TRANSFER,
    EFT_TRANSFER,
    RTC_TRANSFER,
    RTGS_TRANSFER,
    INTERNATIONAL_TRANSFER,
    ATM_WITHDRAWAL,
    CASH_DEPOSIT,
    BRANCH_WITHDRAWAL,
    CARD_PURCHASE_POS,
    CARD_PURCHASE_ONLINE,
    CARD_CONTACTLESS,
    CARD_CASH_ADVANCE,
    BILL_PAYMENT,
    DEBIT_ORDER,
    RECURRING_PAYMENT,
    MOBILE_TRANSFER,
    P2P_PAYMENT,
    QR_CODE_PAYMENT,
    FEE_CHARGE,
    INTEREST_CREDIT,
    REVERSAL,
    SALARY_DEPOSIT
}
