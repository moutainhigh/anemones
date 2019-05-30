package anemones.core;

/**
 * 用于正常的业务异常,代表任务正常失败
 *
 * @author hason
 */
public class AnemonesUnsuccessfulException extends RuntimeException {

    public AnemonesUnsuccessfulException(String msg) {
        super(msg);
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }
}
