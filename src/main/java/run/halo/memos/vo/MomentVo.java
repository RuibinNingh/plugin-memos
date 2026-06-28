package run.halo.memos.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import run.halo.app.extension.MetadataOperator;
import run.halo.memos.Moment;

/**
 * View object served to Thymeleaf via {@code ${momentFinder}}. Shape mirrors
 * the official plugin-moments MomentVo so the xingdu theme templates work
 * unchanged.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class MomentVo {

    private MetadataOperator metadata;
    private Moment.MomentSpec spec;
    private ContributorVo owner;
    private Stats stats;
}
