package bubble.model.app;

import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.collection.HasPriority;

public class AppDataView implements HasPriority {

    @Getter @Setter private AppDataPresentation presentation = AppDataPresentation.app;
    @Getter @Setter private String name;
    @Getter @Setter private Integer priority = 0;

}
