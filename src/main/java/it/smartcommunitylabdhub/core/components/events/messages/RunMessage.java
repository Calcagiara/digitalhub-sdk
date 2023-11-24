package it.smartcommunitylabdhub.core.components.events.messages;

import it.smartcommunitylabdhub.core.components.events.messages.interfaces.Message;
import it.smartcommunitylabdhub.core.models.entities.run.Run;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RunMessage implements Message {
    private Run runDTO;
}
