package cn.sh.command;

/**
 * 具体命令实现
 */
public class ConcreteCommand implements Command {

    private Reciver reciver;

    public ConcreteCommand(Reciver reciver) {
        this.reciver = reciver;
    }

    @Override
    public void execute() {
        this.reciver.action();
    }
}
