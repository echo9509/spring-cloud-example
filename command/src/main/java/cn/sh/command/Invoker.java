package cn.sh.command;

/**
 * 客户端调用者
 */
public class Invoker {

    private Command command;

    public Invoker(Command command) {
        this.command = command;
    }

    public void action() {
        this.command.execute();
    }
}
