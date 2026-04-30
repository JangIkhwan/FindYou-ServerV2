package com.kuit.findyou.global.external.dto;

import java.util.List;

public record ProtectingAnimalPageResult(
        int pageNo,
        int numOfRows,
        int totalCount,
        List<ProtectingAnimalItemDTO> items
) {

    public boolean isLastPage() {
        if (numOfRows <= 0) {
            return true;
        }
        return pageNo * numOfRows >= totalCount;
    }
}
