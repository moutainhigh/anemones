package anemones.core.support;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ImportantWork extends TestWork {


    @Override
    public String queue() {
        return "important";
    }

    @Override
    public int weight() {
        return 100;
    }
}
