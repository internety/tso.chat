package tso.chat.exceptions;

/**
 * Created by reax on 09.11.17.
 */
public class UplayDownException extends Exception {
    public UplayDownException() {
    }

    public UplayDownException(String message) {
        super(message);
    }

    public UplayDownException(String message, Throwable cause) {
        super(message, cause);
    }

    public UplayDownException(Throwable cause) {
        super(cause);
    }

    public UplayDownException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
