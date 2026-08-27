package com.kuit.findyou.domain.report.service.sync.memory_usage;

import com.kuit.findyou.global.external.dto.ProtectingAnimalItemDTO;

import java.util.AbstractList;

final class LazyProtectingAnimalItemList extends AbstractList<ProtectingAnimalItemDTO> {
    /*
    * 성능 테스트 시에 mock 데이터가 힙 메모리 성능 측정에 영향을 주지 않도록
    * mock 데이터를 Lazy하게 생성하기 위한 추상 리스트
    * */
    private final int size;

    LazyProtectingAnimalItemList(int size) {
        this.size = size;
    }

    @Override
    public ProtectingAnimalItemDTO get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(index);
        }

        String noticeNo = "API-" + index;
        return new ProtectingAnimalItemDTO(
                "20240718",
                "서울 강남구 테스트장소",
                "개",
                "진돗개",
                "갈색",
                "3",
                "5",
                noticeNo,
                "20240718",
                "20240725",
                "https://example.com/protecting/" + noticeNo + ".jpg",
                null,
                "M",
                "Y",
                "synthetic worst-case workload",
                "강남구 보호소",
                "02-0000-0000",
                "서울 강남구 테헤란로 1",
                "담당자",
                "강남구청"
        );
    }

    @Override
    public int size() {
        return size;
    }
}
