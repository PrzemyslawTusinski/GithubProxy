package interview.project.Github.data;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
public class ProxyErrorResult {
    Integer errorCode;
    String message;
}