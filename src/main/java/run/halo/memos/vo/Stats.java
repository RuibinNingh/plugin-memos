package run.halo.memos.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Stats {
    private Integer upvote;
    private Integer totalComment;
    private Integer approvedComment;

    public static Stats empty() {
        return Stats.builder()
            .upvote(0)
            .totalComment(0)
            .approvedComment(0)
            .build();
    }
}
