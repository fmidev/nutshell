package nutshell;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IndexedException extends Exception {

    public IndexedException(int index){
        super("Unnamed exception");
        this.index = index;
    }

    public IndexedException(int index, String message){
        super(message);
        this.index = index;
    }


    static public String[] split(String s){
        String[] result = {"",""};
        if (s == null){
            return result;
        }
        Matcher matcher = splitter.matcher(s);
        if (matcher.matches()){
            result[0] = matcher.group(1);
            result[1] = matcher.group(2);
        }
        else {
            result[0] = ""; // or NaN, or something fpr Integer.parseInt(s) to fail
            result[1] = s;
        }
        return result;
    }

    public final int index;

    @Override
    public String getMessage() {
        return String.format("%d %s", index, super.getMessage());
    }

    static final Pattern splitter = Pattern.compile("^\\s*(\\d+)\\s+(.*)$");


    public static void main(String[] args) {

        try {

            if (args.length == 0) {
                throw new IndexedException(1, "You did not give arguments");
            }
            else {
                if (args.length == 1) {
                    String[] msg = split(args[0]);
                    throw new IndexedException(Integer.parseInt(msg[0]), msg[1]);
                }
                else {
                    int index = Integer.parseInt(args[0]);
                    if (args.length == 2) {
                        throw new IndexedException(index, args[1]);
                    }
                    throw new IndexedException(index, args[1] + " - discarding trailing args: " + args[2] + "...");
                }
            }

        }
        catch (IndexedException e) {
            e.printStackTrace();
            System.out.println(String.format("And the numeric code was: %d", e.index));
        }

    }
}
