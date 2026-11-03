# 교토 보완 조사 반영 기록

반영일: 2026-11-03. 원자료: [교토 보완 검증 결과 PDF](kyoto-followup-report.pdf), 19쪽.

PDF를 읽고 표·좌표·사진 카드·출처 주소를 대조해 문서를 갱신함. 이번 작업은 제출 자료의 프로젝트 반영이며 외부 웹사이트를 독립적으로 재검증한 작업이 아님. 아래 ‘확보’는 PDF의 조사 보고에 근거함. 출처 열람일은 PDF가 보고한 2026-11-03(Asia/Seoul), 개별 시·분 미기록임. 서버의 checkedAt·checkedBy·rightsConfirmed·공개 상태는 변경하지 않음.

원본 SHA-256: `8b7abe954e51b4dec6f8d5fe9277c7dbaa288cbcab68f8eb08dd511abf29e53d`.

## 반영 결과와 남은 작업

| 과제 | 이번에 확보한 자료 | 반영·남은 작업 |
| --- | --- | --- |
| Q1 좌표 | Wikidata 도시 대표점, WGS84 설명, 구조화 데이터 이용 정책 | 후보 latitude 35.01161 / longitude 135.76811을 문서에 반영. 팀의 대표점 채택은 남음 |
| Q2 사진 | P2의 저작자·CC BY 2.5 원문·파일 정보·표시 문구 | 최우선 사진 후보로 정리. 직접 이미지 URL, 실제 이미지 검수, alt·캡션과 표시 방식 승인 필요 |
| Q3 한국 출발 | Peach의 기간 명시 ICN/GMP→KIX 운항계획, T2 이동, 하루카·버스 조건 | 적용 기간을 명시해 접근 초안 보완. 여행일별 운항·좌석·막차를 확정한 것은 아님 |
| Q4 경험·현지 이동 | 경험 3개 상세 장소 근거와 교토역 출발 동선 | 사용자 문안·내부 근거를 갱신. 완전한 무단차 접근·최단 경로라고 주장하지 않음 |
| Q5 안전 | 특정 지역 경보 및 상시 안내, 동적 화면 확인 한계 | 교토 현행 미발령은 미확인. `UNVERIFIED`, 안전 항목 0개 제안 유지 |

좌표를 채택하고 이름과 함께 검토하면 `PREPARING` 검토가 가능함. `PUBLIC`에는 사진 최소 1장과 alt 및 모든 선정 콘텐츠의 출처 검토가 추가로 필요함. 방문 시기 등 이번 PDF에서 다시 조사하지 않은 내용은 이전 검토 상태를 유지함. 안전정보 미확인만으로 일반 공개를 막지는 않음.

## 좌표 후보 카드 — PDF 2쪽

- 대표점: Wikidata Q34600 교토 도시 항목의 coordinate location. 시청이나 정확한 행정 중심으로 설명하지 않음.
- 원표기: 북위 35°0′41.8″, 동경 135°46′5.2″.
- 변환: `35 + 41.8/3600` → `35.01161`, `135 + 46/60 + 5.2/3600` → `135.76811` (소수점 5자리 반올림). 계산은 이번 편집에서도 확인함.
- 지도 배열 후보: `[135.76811, 35.01161]` (경도, 위도).
- PDF가 보고한 좌표계·이용 근거: N2 WGS84, N3 구조화 데이터 CC0. 정밀도는 측량 정확도를 의미하지 않음. 원자료 참조는 일본어 위키백과이며 공식 측량 데이터로 취급하지 않음.
- 기존 S29 좌표는 이 후보와 섞지 않음. 실제 지도 코드에는 아직 적용하지 않음.

## 사진 검토 카드 — PDF 3~5쪽

### P2: 우선 검토 후보

| 항목 | PDF가 보고한 내용 |
| --- | --- |
| 파일 페이지 | N4, Arashiyama Togetsukyo-bridge.jpg |
| 저작자 | Tomomarusan, 본인 제작 표시 |
| 선택 라이선스 | CC BY 2.5, 설명 N5·법률 원문 N6 |
| 파일 정보 | JPEG, 3,008 × 2,000 = 6,016,000픽셀, 표시 용량 5.89 MB |
| 사용·변경 근거 | PDF는 상업 이용·변경 허용과 저작자·작품명·출처·라이선스 및 변경 표시 조건을 보고함. 서비스 적용 전 관리자 검토 필요 |
| 미완료 | 직접 이미지 URL·실제 이미지 검수·정확한 파일 바이트 수·alt·캡션·사용 화면의 표시 방식 |

PDF에는 이미지 검색 결과를 언급하는 문장이 있지만 실제 원본 픽셀 검수 완료라고 하지 않음. PDF에 설명이 있다는 이유만으로 이미지를 본 것으로 처리하지 않음. 기존 초안의 ‘다리 너머 호린지’ alt도 확정하지 않음.

저작자표시 초안(실제 적용 승인 전):

```text
사진: Tomomarusan
작품명: Arashiyama Togetsukyo-bridge
출처: Wikimedia Commons
https://commons.wikimedia.org/wiki/File:Arashiyama_Togetsukyo-bridge.jpg
라이선스: CC BY 2.5
https://creativecommons.org/licenses/by/2.5/
변경: 실제 사용 파일에 수행한 변경을 기록
```

### P1·P3: 추가 검토 후보

- P1: PDF는 6,473 × 4,315, 11.84 MB로 보고함. 업로드 전 용량 조정과 실제 이미지 검수 필요. 사진 라이선스와 시설의 상업 촬영 제한은 다른 문제이므로 촬영연도·규칙 게시연도의 선후만으로 적법성을 단정하지 않음.
- P3: PDF는 5,184 × 3,456, 17.19 MB, CC0 표시와 과거 Unsplash 공개 이력을 보고함. 변환·이력·실제 이미지 확인 필요.
- **P3 주소 충돌:** PDF 5쪽의 성공 주소는 `https://commons.wikimedia.org/wiki/File:Arashiyama,_Kyoto,_Japan_(Unsplash).jpg`이나, 11쪽 N10 표에는 N9 실패 주소와 같은 `https://commons.wikimedia.org/wiki/File:Arashiyama,_Kyoto,Japan(Unsplash).jpg`가 표기됨. 렌더링으로도 표기 차이를 확인함. 앞 주소는 성공 주장 후보로만 보관하고 N10을 재검증 완료 출처로 사용하지 않음.

## 항공·접근 자료의 적용 범위 — PDF 5~7쪽

- N12는 자료 기준일 2026-10-21, 적용 기간 2026-10-25~2027-03-27의 Peach 국제선 계획임. PDF는 ICN→KIX, GMP→KIX 방향을 확인했다고 보고함. 항공편 번호 일부 추출 깨짐을 N13으로 대조했으나 개별 날짜 예약검색은 수행하지 않음. 사용자 초안에는 불필요한 편명·출발 시각을 넣지 않음.
- N13의 제목은 도쿄 안내이나, 보고서는 본문 서울→간사이 표를 보완 근거로 사용함. 적용 기간은 N13에서 추정하지 않고 N12에 연결함.
- N14 영문 페이지의 표는 2026-10-24까지이므로 이번 현재 경로 근거에서 제외함.
- Peach T2 도착 시 무료 셔틀로 에어로플라자·간사이공항역 측에 접근하는 경로는 N15에 연결함. 다른 항공사에 일괄 적용하지 않음.
- N16은 할인 승차권 상품 자료임. 상품 구매 자격·가격·좌석 조건을 하루카 전체 탑승 규칙으로 확대하지 않음.
- N18·N19는 KIX→교토역 하치조구치 버스 경로와 방향별 조건을 구분함. PDF는 KIX 출발 선착순, KIX행 예약 좌석으로 보고함. 정확한 승차장 번호는 미확인으로 남김.
- N20은 검색 양식, N21은 특정 갱신시점 운행정보임. 특정 여행일 막차나 조사시각 실시간 정상 운행을 입증하지 않음.

## 추가 출처 색인

`N`은 PDF의 C절 ID이며 PDF 말미의 숫자 각주 번호와 다름. `S`는 이전 kyoto.txt의 ID임. 다음은 갱신한 초안의 핵심 출처 색인이고 N1~N40 전체 대장·원문 제목·근거 섹션·날짜는 PDF 10~14쪽에 보존함. URL이 실려 있다는 것만으로 이번 편집에서 직접 열람한 것은 아님.

| ID | 자료·발행자 | URL | PDF 보고 수준·용도 |
| --- | --- | --- | --- |
| N1 | Kyoto / Wikidata 기여자 | https://www.wikidata.org/wiki/Q34600 | 본문·도시 좌표 |
| N2 | coordinate location / Wikidata | https://www.wikidata.org/wiki/Property:P625 | 본문·좌표계 |
| N3 | Wikidata:Licensing / Wikidata | https://www.wikidata.org/wiki/Wikidata:Licensing | 본문·구조화 데이터 정책 |
| N4 | Arashiyama Togetsukyo-bridge / Tomomarusan, Commons | https://commons.wikimedia.org/wiki/File:Arashiyama_Togetsukyo-bridge.jpg | 파일 설명 본문, 픽셀 검수 아님 |
| N5 | Attribution 2.5 Generic / Creative Commons | https://creativecommons.org/licenses/by/2.5/ | 라이선스 설명 |
| N6 | Attribution 2.5 Generic Legal Code / Creative Commons | https://creativecommons.org/licenses/by/2.5/legalcode | 법률 원문 열람 보고 |
| N12 | Peach_FlightSchedule_20261021 / Peach Aviation | https://www.flypeach.com/application/files/8917/9245/8562/20261021_W26_20261025-20270327_int_JP.pdf | 기간 명시 PDF, 편명 추출 일부 깨짐 |
| N13 | 생각나면 떠나는 도쿄 / Peach Aviation | https://www.flypeach.com/kr/lm/st/route/new_icn_nrt | 본문 서울→간사이 표 대조 |
| N14 | Seoul feels a lot closer! / Peach Aviation | https://www.flypeach.com/en/lm/st/route/osaka_seoul_gimpo | 기간 만료 표, 현재 근거에서 제외 |
| N15 | Shuttle bus to Terminal 2 Building / Kansai Airports | https://www.kansai-airport.or.jp/en/access/t2 | 상세 본문·T2 접근 |
| N16 | HARUKA One-way Ticket Information / JR-WEST | https://www.westjr.co.jp/travel-information/en/tickets-passes/oneway/haruka/ | 상세 상품 본문·직통/예약, 가격 제외 |
| N17 | Train Usage Guide / JR-WEST | https://www.westjr.co.jp/travel-information/en/train-usage-guide/ | 본문·수하물 안내 |
| N18 | Time table/Fares / Kansai Airport Transportation Enterprise | https://www.kate.co.jp/en/timetable/detail/KY | 노선표·교토 버스 |
| N19 | Instructions / Kansai Airport Transportation Enterprise | https://www.kate.co.jp/en/guide/ | 상세 본문·방향별 예약/수하물 |
| N20 | Search for tickets / JR-WEST | https://www.westjr.co.jp/travel-information/en/tickets-passes/route-search/ | 검색 양식만, 개별일 검색 미실행 |
| N21 | Limited Express Trains Train Status Information / JR-WEST | https://global.trafficinfo.westjr.co.jp/en/express | 동적 화면의 갱신시점 한계 |
| N22 | Senbon Torii / Fushimi Inari Taisha | https://inari.jp/en/map/spot_07/ | 상세 본문·경관/봉납 |
| N23 | Access / Fushimi Inari Taisha | https://inari.jp/en/access/ | 상세 본문·JR 이나리역 |
| N24 | Request for All Visitors / Fushimi Inari Taisha | https://inari.jp/en/request/ | 상세 본문, 2022-01-01 표기·이용 안내 |
| N26 | VISIT / Kiyomizu-dera Temple | https://www.kiyomizudera.or.jp/en/visit/ | 상세 본문·본당/오쿠노인, 과거 팬데믹 문구 제외 |
| N27 | FAQ / Kiyomizu-dera Temple | https://www.kiyomizudera.or.jp/en/faq/ | 상세 본문·경내 접근성/이용 안내 |
| N28 | Comfortable access to Higashiyama / Kyoto Travel | https://www.kyoto.travel/en/getting-around/comfortable-access-to-higashiyama-kiyomizu-dera-temple/ | 상세 본문·지하철/도보 |
| N29 | Kiyomizudera Temple / JNTO | https://faq.japan-travel.jnto.go.jp/en/spot/2199/ | 상세 본문·오르막/주변 길 |
| N30 | Arashiyama Bamboo Grove / JNTO | https://www.japan.travel/en/spot/1141/ | 상세 본문·숲길/도보 |
| N31 | Comfortable access to Saga/Arashiyama / Kyoto Travel | https://kyoto.travel/en/getting-around/comfortable-access-to-saga-arashiyama/ | 상세 본문·JR 접근 |
| N32 | Saga & Arashiyama / Kyoto Travel | https://kyoto.travel/en/areas/saga-arashiyama/ | 지역 본문·경관/보전 |
| N33 | Things to Keep in Mind while Exploring Kyoto and its Kiyomizu, Arashiyama and Gion Areas / Kyoto Travel, Misako Tateoka | https://kyoto.travel/en/travel-inspiration/things-to-keep-in-mind-while-exploring-kyoto-and-its-kiyomizu-arashiyama-and-gion-areas/ | 상세 본문, 2023-11-17·통행/쓰레기/짐. 벌금 수치 제외 |
| N34 | 일본 국가 상세 / 대한민국 외교부 | https://www.0404.go.kr/ntnSafetyInfo/183/detail | 특정 지역 경보 표시 보고, 교토 미발령 확정 아님 |
| N35 | Safety Information / Kyoto Travel | https://www.kyoto.travel/en/safety-info/ | 상세 본문·상시 대응 안내 |
| N36 | 警報・注意報 / 일본 기상청 | https://www.jma.go.jp/bosai/warning/#area_type=offices&area_code=260000 | 화면 일부 추출, 지역별 현행 상태 확인 실패 |
| N39 | Kyoto Nishiki Food Market / Kyoto Travel | https://kyoto.travel/en/destinations/kyoto-nishiki-food-market/ | 상세 본문·취식 안내 재확인 보고 |

## 다음 작업

1. Q1 후보의 대표점 채택과 태그·경험 순서를 팀이 검토함.
2. P2의 실제 이미지 링크를 열어 확인하고 alt·캡션·저작자표시 위치를 확정함. 사진 3장 모두를 검토해야 첫 게시가 가능한 것은 아님.
3. 방문 시기·소개 등 기존 자료와 새 근거를 포함해 최종 선정 콘텐츠를 검토함. 일부 본문만 검토 완료라면 미완성 후보는 미선정으로 보관함.
4. 한국 출발 문안은 N12의 계획 적용 기간을 보존하고 기간 이후 재확인함.
5. 공개 상태 변경·사진 업로드·DB 등록은 별도 구현/검토 단계에서 진행함.
