package run.halo.memos.vo;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MomentTagVo {
    String name;
    String permalink;
    Integer momentCount;
}
