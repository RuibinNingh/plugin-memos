package run.halo.memos.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import run.halo.app.core.extension.User;

/**
 * Owner of a moment, derived from a Halo {@link User}. Mirrors the shape the
 * xingdu theme reads ({@code owner.name}, {@code owner.avatar},
 * {@code owner.displayName}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ContributorVo {

    private String name;
    private String avatar;
    private String bio;
    private String displayName;

    public static ContributorVo from(User user) {
        return ContributorVo.builder()
            .name(user.getMetadata().getName())
            .displayName(user.getSpec().getDisplayName())
            .avatar(user.getSpec().getAvatar())
            .bio(user.getSpec().getBio())
            .build();
    }
}
