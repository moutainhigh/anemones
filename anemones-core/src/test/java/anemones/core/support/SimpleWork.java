package anemones.core.support;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SimpleWork extends TestWork {


    @Override
    public String queue() {
        return "simple";
    }


}
