package com.example.moneymap.features.transaction.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransactionListResponse {
    private List<TransactionResponse> items;
    private int offset;
    private int limit;
    private long total;
}
