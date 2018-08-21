package cn.sh.command;

public class Client {

    public static void main(String[] args) {
        Reciver reciver = new Reciver();
        Command command = new ConcreteCommand(reciver);
        Invoker invoker = new Invoker(command);
        invoker.action(); //客户端通过调用者来执行命令
    }
}
