import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;

// Viewer for "Adventure A. Planet of Death" by Artic Computing
public class ZXAdvAViewer {

    public static void main(String[] args) {

        if (args.length != 1) {
            System.out.println("Please supply a path to snapshot as a program parameter");
            return;
        }

        final byte[] zxMem;
        try {
            zxMem = loadSna(args[0]);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // words are specified in a following way:
        // 4 bytes - word first 4 characters
        // 1 byte - word index/code
        // here we scan word list and reverse it into index->words map
        Map<Integer, List<String>> words = new HashMap<>();
        int wt = 0x7090;
        while (true) {
            if ((zxMem[wt] & 0xFF) == 0xFF) {
                break;
            }
            String val = new String(Arrays.copyOfRange(zxMem, wt, wt + 4));
            int code = 0xFF & zxMem[wt + 4];
            List<String> ll;
            if (words.containsKey(code)) {
                ll = words.get(code);
            } else {
                ll = new ArrayList();
                words.put(code, ll);
            }
            ll.add(val);
            wt += 5;
        }

        // a list of 2-byte pointers to room descriptions, starting at 0x6834
        Map<Integer, String> rooms = new HashMap<>();
        for (int rp = 0x6834, ri = 0 ; ri < 21; ri++, rp += 2) {
            // if ((zxMem[ip] & 0xFF) == 0xFF) break;
            int ptr = getPtr(zxMem, rp);
            rooms.put(ri, getStr(zxMem, ptr));
        }

        System.out.println("Rooms:");

        // room-transition scripts are indexed by a list starting at 0x7894,
        // with each entry being a pointer to a list
        // list entries are of two bytes. First byte is either a word index (direction), or 0xFF (an end marker)
        // Second byte is a index of a room where you'll get if you'll take that direction
        int rtb = 0x7894;
        for (int i = 0; i < 20; i++) {
            int rf = getPtr(zxMem, rtb + i * 2);
            System.out.println("Room #" + i);
            System.out.println(rooms.get(i).replace("\r", "."));
            while (true) {
                int cv = zxMem[rf] & 0xFF;
                if (cv == 0xFF) break;
                System.out.println("  " + words.get(cv).get(0) + " -> " + (zxMem[rf + 1] & 0xFF));
                rf += 2;
            }
        }

        System.out.println("Items:");

        // a list of 2-byte pointers to item names, starting at 0x6E5D
        // A handler for "INVENTORY" command uses this list to convert item indexes into readable names
        Map<Integer, String> items = new HashMap<>();
        for (int ip = 0x6E5D, ii = 0 ; ii < 28; ii++, ip += 2) {
            // if ((zxMem[ip] & 0xFF) == 0xFF) break;
            int ptr = getPtr(zxMem, ip);
            items.put(ii, getStr(zxMem, ptr));
        }

        // a list starting from $6E40 contains item's locations
        for (int ii = 0, il = 0x6E40; (zxMem[il] & 0xFF) != 0xFF; il++, ii++) {
            int i = zxMem[il] & 0xFF;
            String loc = (i == 0xFE) ? "Inventory" : ((i == 0xFD) ? "Worn" : ((i == 0xFC) ? "Nowhere" : String.valueOf(i)));
            System.out.println("  " + String.valueOf(ii) + ": " + items.get(ii) + "  @  " + loc);
        }

        System.out.println("Checkers:");
        // 0x6001; 0x6013; 0x6028; 0x6031; 0x6046; 0x6056; 0x6068; 0x6078; 0x6088;
        int[] checkers = new int[9];
        for (int i = 0; i < checkers.length; i++) {
            int ptr = getPtr(zxMem, 0x609D + i * 2);
            System.out.print("0x" + Integer.toHexString(ptr) + "; ");
            checkers[i] = ptr;
        }
        System.out.println("");
        Map<Integer, String> knownCheckers = new HashMap<>();
        knownCheckers.put(0, "Current room is ");
        knownCheckers.put(1, "Player's inventory or current location contains item ");
        knownCheckers.put(2, "Random generator returned true ");
        knownCheckers.put(3, "Player's inventory or current location doesn't contain item ");
        knownCheckers.put(4, "Player wears an item ");
        knownCheckers.put(5, "Non-zero global flag ");
        knownCheckers.put(7, "Zero global flag ");
        knownCheckers.put(8, "Player's inventory contains item ");

        System.out.println("Handlers:");
        // 0x613a; 0x61d7; 0x6237; 0x62b8; 0x62ef; 0x6345; 0x6358; 0x635d; 0x6362; 0x636a; 0x6377; 0x6388; 0x6392; 0x6395; 0x63a8; 0x6492; 0x64a6; 0x64b6; 0x64c9; 0x64f8; 0x64c4; 0x5e71; 0x64c4; 0x64c4; 0x64c4;
        int[] handlers = new int[25];
        for (int i = 0; i < handlers.length; i++) {
            int ptr = getPtr(zxMem, 0x6108 + i * 2);
            System.out.print("0x" + Integer.toHexString(ptr) + "; ");
            handlers[i] = ptr;
        }
        System.out.println("");

        System.out.println("Global scripts:");

        processScripts(zxMem, words, 0x746D, 150, knownCheckers);

//        System.out.println("Global scripts 2:");
//
//        processScripts(zxMem, words, 0x7EC9, 10);

    }

    private static void processScripts(byte[] zxMem, Map<Integer, List<String>> words, int gsb, int limit, Map<Integer, String> knownCheckers) {

        int count = 0;

        // global scripts are 6-byte entries starting with 0x746D
        // first byte is either 0, or 0xFF (no word), or word-related
        // second byte is used in conjunction with word
        // bytes +2 and +3 contain pointer to a list of condition checkers which should all be satistfied for script to apply
        // bytes +4 and +4 contian pointer to a list of actions which will be executied if all conditions are satisfied
        while (true) {
            int ib = zxMem[gsb] & 0xFF;
            if (ib != 0) {
                int pb = zxMem[gsb + 1] & 0xFF;
                if (ib != 0xFF) {
                    System.out.println("  Word:" + words.get(ib) + "  &   " + String.valueOf(words.get(pb)));
                } else {
                    System.out.println("  No word");
                }
            }

            System.out.print("    Checkers:");
            int clp = getPtr(zxMem, gsb + 2);  // pointer to list of checkers
            int cc = 0;
            while (true) {
                int cidx = zxMem[clp] & 0xFF;  // checker index
                if (cidx == 0xFF || cidx == 6) break;  // checker 6 is special
                int cpr = zxMem[clp + 1] & 0xFF; // checker param
                if (knownCheckers.containsKey(cidx)) {
                    System.out.print(knownCheckers.get(cidx) + String.valueOf(cpr) + " ;");
                } else {
                    System.out.print("" + cidx + "(" + cpr + ");");
                }
                clp = clp + 2;
                if (cc++ > 20) break;    // artificial limit
            }
            System.out.println("");

            List<Integer> finalHandlers = Arrays.asList(new Integer[] {13});

            System.out.print("    Handlers:");
            int hlp = getPtr(zxMem, gsb + 4);    // pointer to list of handlers
            int hc = 0;
            while (true) {
                int hidx = zxMem[hlp] & 0xFF;  // handler index
                if (hidx == 0xFF) break;
                int hpr = zxMem[hlp + 1] & 0xFF; // handler param
                System.out.print("" + hidx + "(" + hpr + ");");
                if (finalHandlers.contains(hidx))  break;
                hlp = hlp + 2;
                if (hc++ > 30) break;  // artificial limit
            }
            System.out.println("");

            gsb += 6;

            count += 1;
            if (count > limit) break;
        }
    }

    private static int getPtr(byte[] zxMem, int addr) {
        return (0xFF & zxMem[addr]) + (0xFF & zxMem[addr + 1]) * 256;
    }

    private static String getStr(byte[] zxMem, int start) {
        int i = start;
        while (zxMem[i] != 0) i++;
        return new String(zxMem,start, i - start);
    }

    private static byte[] loadSna(String filename) throws Exception {
        var file = new File(filename);
        InputStream in = new BufferedInputStream(new FileInputStream(file));
        byte[] snaData = new byte[50000];
        int result = in.read(snaData);
        byte[] zxMem = new byte[65536];
        System.arraycopy(snaData, 27, zxMem, 16384, 49152 );

        return zxMem;
    }



}

