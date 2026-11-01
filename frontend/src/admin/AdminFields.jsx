import React from 'react';

export function Field({ label, children, hint }) {
  return (
    <label className="adm-field">
      <span>{label}</span>
      {children}
      {hint && <small>{hint}</small>}
    </label>
  );
}
export function Badge({ children, tone = '' }) {
  return <span className={`adm-badge ${tone}`}>{children}</span>;
}
export function Empty({ title, children }) {
  return (
    <div className="adm-empty">
      <h3>{title}</h3>
      <p>{children}</p>
    </div>
  );
}
export function SafetyForm({ value, onChange }) {
  const update = (key, v) =>
    onChange({ ...value, [key]: v, ...(key === 'confirmed' ? {} : { confirmed: false }) });
  return (
    <div className="adm-safety-form">
      <div className="adm-grid two">
        <Field label="자료 검토 상태">
          <select value={value.reviewState} onChange={(e) => update('reviewState', e.target.value)}>
            <option value="UNVERIFIED">미확인</option>
            <option value="PARTIAL">일부 확인</option>
            <option value="CHECKED">확인 완료</option>
          </select>
        </Field>
        <Field label="항목 종류">
          <select
            value={value.kind}
            onChange={(e) =>
              onChange({
                ...value,
                kind: e.target.value,
                assessment: e.target.value === 'OFFICIAL_ADVISORY' ? 'NOT_ISSUED' : 'CAUTION',
                officialLevel: '',
                confirmed: false,
              })
            }
          >
            <option value="LOCAL_CAUTION">현지 주의사항</option>
            <option value="OFFICIAL_ADVISORY">공식 여행경보</option>
          </select>
        </Field>
      </div>
      {value.reviewState === 'UNVERIFIED' ? (
        <div className="adm-notice">
          미확인으로 게시하면 검토된 안전 항목을 제공하지 않습니다. 미확인은 안전함을 뜻하지 않아요.
        </div>
      ) : (
        <>
          {value.kind === 'OFFICIAL_ADVISORY' && (
            <div className="adm-grid two">
              <Field label="공식 발령 여부">
                <select
                  value={value.assessment}
                  onChange={(e) =>
                    onChange({
                      ...value,
                      assessment: e.target.value,
                      officialLevel: '',
                      confirmed: false,
                    })
                  }
                >
                  <option value="NOT_ISSUED">공식 미발령 확인</option>
                  <option value="ISSUED">발령</option>
                </select>
              </Field>
              {value.assessment === 'ISSUED' && (
                <Field label="공식 경보 단계">
                  <select
                    value={value.officialLevel}
                    onChange={(e) => update('officialLevel', e.target.value)}
                  >
                    <option value="">선택</option>
                    {['LEVEL_1', 'LEVEL_2', 'LEVEL_3', 'LEVEL_4', 'SPECIAL'].map((v, i) => (
                      <option key={v} value={v}>
                        {i < 4 ? `${i + 1}단계` : '특별여행주의보'}
                      </option>
                    ))}
                  </select>
                </Field>
              )}
            </div>
          )}
          <Field label="안전정보 제목">
            <input
              maxLength={200}
              value={value.title}
              onChange={(e) => update('title', e.target.value)}
            />
          </Field>
          <Field label="주의사항 본문 · 최대 3,000자">
            <textarea
              maxLength={3000}
              value={value.body}
              onChange={(e) => update('body', e.target.value)}
            />
          </Field>
          <div className="adm-grid two">
            <Field label="원문 적용 범위">
              <select value={value.scope} onChange={(e) => update('scope', e.target.value)}>
                <option value="COUNTRY_WIDE">국가 전체</option>
                <option value="REGION">지역</option>
                <option value="CITY">도시</option>
                <option value="SPECIFIC_PLACE">특정 장소</option>
              </select>
            </Field>
            <Field label="도시 적용 범위">
              <select value={value.coverage} onChange={(e) => update('coverage', e.target.value)}>
                <option value="WHOLE_CITY">도시 전체</option>
                <option value="PART_OF_CITY">도시 일부</option>
              </select>
            </Field>
          </div>
          <Field label="도시 적용 근거 · 최대 4,000자">
            <textarea
              maxLength={4000}
              value={value.scopeNote}
              onChange={(e) => update('scopeNote', e.target.value)}
            />
          </Field>
          <Field label="공식 출처 URL">
            <input
              type="url"
              value={value.sourceUrl}
              onChange={(e) => update('sourceUrl', e.target.value)}
            />
          </Field>
          <Field label="출처 근거 메모 · 최대 4,000자">
            <textarea
              maxLength={4000}
              value={value.evidence}
              onChange={(e) => update('evidence', e.target.value)}
            />
          </Field>
          <label className="adm-check">
            <input
              type="checkbox"
              checked={value.confirmed}
              onChange={(e) => update('confirmed', e.target.checked)}
            />
            현재 문구·적용 범위·원문 근거를 대조했습니다.
          </label>
        </>
      )}
      <p className="adm-hint">
        현재 시연은 안전 항목 1개의 편집 흐름입니다. 확인 완료는 여행 안전 보장이 아닙니다.
      </p>
    </div>
  );
}
