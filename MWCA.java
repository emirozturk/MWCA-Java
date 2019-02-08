package net.emirozturk;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

class MWCAFile
{
    public Byte[] s1, s2, bv, s3, d1, d2;
    public Byte redundantBits, escapeLength;
}
public class Main {

    static boolean[] isChar = new boolean[256];
    static int maxWordLength=0;
    static byte escapeLength=0;

    static HashMap<String, Byte> d1Dict = new HashMap<>(255);
    static HashMap<String, Short> d2Dict = new HashMap<>(65536);

    public static void main(String[] args) throws IOException {
        if(args.length==3) {
            String fileName = args[0];
            String compressionMode = args[1];
            String fileMode = args[2];
            for (int i = 0; i < isChar.length; i++)
                isChar[i] = Character.isLetterOrDigit(i);
            if (compressionMode.equals("c")) {
                byte[] content = Files.readAllBytes(Paths.get(fileName));
                MWCAFile mf = compress(content);
                if (fileMode.equals("s"))
                    writeSingleStream(fileName, mf);
                else if (fileMode.equals("m"))
                    writeMultiStream(fileName, mf);
            } else if (compressionMode.equals("d")) {
                MWCAFile mf = new MWCAFile();
                if (fileMode.equals("s"))
                    mf = readSingleStream(fileName);
                else if (fileMode.equals("m"))
                    mf = readMultiStream(fileName);
                byte[] content = decompress(mf);
                Files.write(Paths.get((fileName + ".decompressed")), content);
            }
        }
        else
            System.out.println("Wrong Argument Count");
    }

    static MWCAFile compress(byte[] content) {
        MWCAFile mf = new MWCAFile();
        mf = firstPass(content, mf);
        mf = secondPass(content, mf);
        return mf;
    }

    static MWCAFile firstPass(byte[] content, MWCAFile mf) {
        int pointer = 0, start;
        String word = "";
        HashMap<String, Integer> wordFreq = new HashMap<>();

        while (pointer < content.length)
        {
            start = pointer;
            if (pointer < content.length && isChar[content[pointer]])
                while (isChar[content[pointer]] && pointer < content.length)
                    pointer++;
            else
            {
                if (content[pointer] != ' ')
                    while (pointer < content.length && !isChar[content[pointer]])
                        pointer++;
                else
                {
                    pointer++;
                    if (pointer < content.length && isChar[content[pointer]])
                    {
                        start = pointer;
                        while (pointer < content.length && isChar[content[pointer]])
                            pointer++;
                    }
                    else
                        while (pointer < content.length && !isChar[content[pointer]])
                            pointer++;
                }
            }
            for (int i = start; i < pointer; i++)
                word += content[i];

            if (word.length() > maxWordLength)
                maxWordLength = word.length();

            if (wordFreq.containsKey(word))
                wordFreq.put(word,wordFreq.get(word)+1);
            else
                wordFreq.put(word, 1);
            word = "";
        }
        if (word.length() > 0)
        {
            if (word.length() > maxWordLength)
                maxWordLength = word.length();
            if (wordFreq.containsKey(word))
                wordFreq.put(word,wordFreq.get(word)+1);
            else
                wordFreq.put(word, 1);
        }
        String[] words = sortAndTakeElements(wordFreq,false,255+65536);

        for (int i = 0; i < 255; i++)
        {
            if (i == words.length) break;
            d1Dict.put(words[i], (byte)(i + 1));
        }
        for (int i = 255; i < 255 + 65536; i++)
        {
            if (i == words.length) break;
            d2Dict.put(words[i], (short)(i - 255));
        }
        escapeLength = (byte)(Math.ceil(Math.log(maxWordLength)/Math.log(256)));
        mf.escapeLength = escapeLength;

        ArrayList<Byte> d1 = new ArrayList<>();
        ArrayList<Byte> d2 = new ArrayList<>();

        for (int i = 0; i < 255; i++)
        {
            if (i == words.length) break;
            d1.addAll(convertToLengthWord(words[i]));
        }
        for (int i = 255; i < 255 + 65536; i++)
        {
            if (i == words.length) break;
            d2.addAll(convertToLengthWord(words[i]));
        }
        mf.d1 = d1.toArray(new Byte[0]);
        mf.d2 = d2.toArray(new Byte[0]);
        return mf;
    }

    private static MWCAFile secondPass(byte[] content, MWCAFile mf) {
        ArrayList<Boolean> bv = new ArrayList<>();
        ArrayList<Byte> s1 = new ArrayList<>();
        ArrayList<Short> s2 = new ArrayList<>();
        ArrayList<Byte> s3 = new ArrayList<>();

        int pointer = 0, start;
        String word = "";

        while (pointer < content.length)
        {
            start = pointer;
            if (pointer < content.length && isChar[content[pointer]])
                while (pointer < content.length && isChar[content[pointer]])
                    pointer++;
            else
            {
                if (content[pointer] != ' ')
                    while (pointer < content.length && !isChar[content[pointer]])
                        pointer++;
                else
                {
                    pointer++;
                    if (pointer < content.length && isChar[content[pointer]])
                    {
                        start = pointer;
                        while (pointer < content.length && isChar[content[pointer]])
                            pointer++;
                    }
                    else
                        while (pointer < content.length && !isChar[content[pointer]])
                            pointer++;
                }
            }
            for (int i = start; i < pointer; i++)
                word += content[i];

            if (d1Dict.containsKey(word))
            {
                bv.add(false);
                s1.add(d1Dict.get(word));
            }
            else if (d2Dict.containsKey(word))
            {
                bv.add(true);
                s2.add(d2Dict.get(word));
            }
            else
            {
                bv.add(false);
                s1.add((byte)0);
                s3.addAll(convertToLengthWord(word));
            }
            word = "";
        }
        if (word.length() > 0)
        {
            if (d1Dict.containsKey(word))
            {
                bv.add(false);
                s1.add(d1Dict.get(word));
            }
            else if (d2Dict.containsKey(word))
            {
                bv.add(true);
                s2.add(d2Dict.get(word));
            }
            else
            {
                bv.add(false);
                s1.add((byte)0);
                s3.addAll(convertToLengthWord(word));
            }
        }

        mf.redundantBits = (byte)(8 - (bv.size() % 8));
        mf.s1 = new Byte[s1.size()];
        mf.s2 = new Byte[s2.size() * 2];
        mf.s3 = new Byte[s3.size()];

        if (s1.size() > 0) System.arraycopy(s1.toArray(), 0, mf.s1, 0, s1.size());

        ByteBuffer bb = ByteBuffer.allocate(2*s2.size());
        int i=0;
        if (s2.size() > 0) {
            while (s2.size() > i) {
                bb.putShort(s2.get(i));
                i++;
            }
        }
        mf.s2 = toNonPrimitiveByteArray(bb.array());

        if (s3.size() > 0) System.arraycopy(s3.toArray(), 0, mf.s3, 0, s3.size());

        mf.bv = toByteArray(bv.toArray(new Boolean[0]));

        return mf;
    }

    static ArrayList<Byte> convertToLengthWord(String word) {
        ArrayList<Byte> list = new ArrayList<>();
        byte[] length = ByteBuffer.allocate(4).putInt(word.length()).array();
        for (int i = 0; i < escapeLength; i++)
            list.add(length[i]);
        byte[] wordBytes = word.getBytes();
        for (int i = 0; i < word.length(); i++)
            list.add(wordBytes[i]);
        return list;
    }

    static byte[] decompress(MWCAFile mf) {
        ArrayList<Byte> output = new ArrayList<>();

        ArrayList<Byte[]> d1 = new ArrayList<>(255);
        ArrayList<Byte[]> d2 = new ArrayList<>(65536);
        int s1Counter = 0, s2Counter = 0, s3Counter = 0;

        int code = 0;

        boolean prev = false, current = false;

        escapeLength = mf.escapeLength;
        createDictionaries(mf.d1, mf.d1, d1, d2);
        Boolean[] bv = toBoolArray(mf.bv);
        for (int i = 0; i < bv.length - mf.redundantBits; i++)
        {
            if (!bv[i])
            {
                code = mf.s1[s1Counter++];
                if (code == 0)
                {
                    byte[] lengthArray = new byte[escapeLength];
                    for (int j = 0; j < escapeLength; j++)
                        lengthArray[j] = mf.s3[s3Counter++];
                    current = isChar[mf.s3[s3Counter]];
                    if (prev && current)
                        output.add((byte)' ');
                    int length = ByteBuffer.wrap(lengthArray).getInt();
                    for (int j = 0; j < length; j++)
                        output.add(mf.s3[s3Counter++]);
                }
                else
                {
                    current = isChar[d1.get(code)[0]];
                    if (prev && current)
                        output.add((byte)' ');
                    Byte[] array = d1.get(code);
                    for (byte b : array)
                        output.add(b);
                }
            }
            else if (bv[i])
            {
                byte[] part = new byte[2];
                part[0] = mf.s2[s2Counter++];
                part[1] = mf.s2[s2Counter++];
                code = ByteBuffer.wrap(part).getShort();
                current = isChar[d2.get(code)[0]];
                if (prev && current)
                    output.add((byte)' ');
                Byte[] array = d2.get(code);
                for (byte b : array)
                    output.add(b);
            }
            prev = current;
        }
        return toPrimitiveByteArray(output.toArray(new Byte[0]));
    }

    static void createDictionaries(Byte[] d1Array, Byte[] d2Array, ArrayList<Byte[]> d1, ArrayList<Byte[]> d2) {
        byte[] lengthArray = new byte[escapeLength];
        int dictionaryCounter = 0;
        d1.add(new Byte[0]);
        do
        {
            ArrayList<Byte> word = new ArrayList<>();
            for (int j = 0; j < escapeLength; j++)
                lengthArray[j] = d1Array[dictionaryCounter++];

            int length = 0;
            if (lengthArray.length == 1)
                length = lengthArray[0];
            else if (lengthArray.length == 2)
                length = ByteBuffer.wrap(lengthArray).getShort();
            else if (lengthArray.length == 4)
                length = ByteBuffer.wrap(lengthArray).getInt();

            for (int j = 0; j < length; j++)
                word.add(d1Array[dictionaryCounter++]);
            d1.add(word.toArray(new Byte[0]));
            word.clear();
        } while (dictionaryCounter < d1Array.length);
        dictionaryCounter = 0;
        do
        {
            ArrayList<Byte> word = new ArrayList<>();
            for (int j = 0; j < escapeLength; j++)
                lengthArray[j] = d2Array[dictionaryCounter++];

            int length = 0;
            if (lengthArray.length == 1)
                length = lengthArray[0];
            else if (lengthArray.length == 2)
                length = ByteBuffer.wrap(lengthArray).getShort();
            else if (lengthArray.length == 4)
                length = ByteBuffer.wrap(lengthArray).getInt();

            for (int j = 0; j < length; j++)
                word.add(d2Array[dictionaryCounter++]);
            d2.add(word.toArray(new Byte[0]));
        } while (dictionaryCounter < d2Array.length);
    }

    static void writeMultiStream(String fileName, MWCAFile mf) throws IOException {
        byte[] bvFile = new byte[mf.bv.length + 2];
        bvFile[0] = mf.redundantBits;
        bvFile[1] = mf.escapeLength;

        System.arraycopy(mf.bv,0,bvFile,2,mf.bv.length);

        Files.write(Paths.get((fileName + ".bv")), bvFile);
        Files.write(Paths.get((fileName + ".s1")), toPrimitiveByteArray(mf.s1));
        Files.write(Paths.get((fileName + ".s2")), toPrimitiveByteArray(mf.s2));
        Files.write(Paths.get((fileName + ".s3")), toPrimitiveByteArray(mf.s3));
        Files.write(Paths.get((fileName + ".d1")),toPrimitiveByteArray( mf.d1));
        Files.write(Paths.get((fileName + ".d2")),toPrimitiveByteArray( mf.d2));
    }

    static void writeSingleStream(String fileName, MWCAFile mf) throws IOException {
        int fileSize = mf.s1.length + mf.s2.length + mf.bv.length + mf.s3.length + mf.d1.length + mf.d2.length;
        byte[] output = new byte[fileSize + 26];
        output[0] = mf.redundantBits;
        output[1] = mf.escapeLength;

        System.arraycopy(ByteBuffer.allocate(4).putInt(mf.s1.length).array(), 0, output, 2, 4);
        System.arraycopy(ByteBuffer.allocate(4).putInt(mf.s2.length).array(), 0, output, 6, 4);
        System.arraycopy(ByteBuffer.allocate(4).putInt(mf.bv.length).array(),  0, output, 10, 4);
        System.arraycopy(ByteBuffer.allocate(4).putInt(mf.s3.length).array(),  0, output, 14, 4);
        System.arraycopy(ByteBuffer.allocate(4).putInt(mf.d1.length).array(),  0, output, 18, 4);
        System.arraycopy(ByteBuffer.allocate(4).putInt(mf.d2.length).array(),  0, output, 22, 4);
        int totalLength = 26;

        System.arraycopy(toPrimitiveByteArray(mf.s1), 0, output, totalLength, mf.s1.length);
        totalLength += mf.s1.length;

        System.arraycopy(toPrimitiveByteArray(mf.s2), 0, output, totalLength, mf.s2.length);
        totalLength += mf.s2.length;

        System.arraycopy(toPrimitiveByteArray(mf.bv), 0, output, totalLength, mf.bv.length);
        totalLength += mf.bv.length;

        System.arraycopy(toPrimitiveByteArray(mf.s3), 0, output, totalLength, mf.s3.length);
        totalLength += mf.s3.length;

        System.arraycopy(toPrimitiveByteArray(mf.d1), 0, output, totalLength, mf.d1.length);
        totalLength += mf.d1.length;

        System.arraycopy(toPrimitiveByteArray(mf.d2), 0, output, totalLength, mf.d2.length);
        totalLength += mf.d2.length;

        Files.write(Paths.get((fileName + ".mf")), output);
    }

    static MWCAFile readMultiStream(String fileName) throws IOException {
        MWCAFile mf = new MWCAFile();
        mf.s1 = toNonPrimitiveByteArray(Files.readAllBytes(Paths.get((fileName + ".f"))));
        mf.s2 = toNonPrimitiveByteArray(Files.readAllBytes(Paths.get((fileName + ".s"))));
        mf.s3 = toNonPrimitiveByteArray(Files.readAllBytes(Paths.get((fileName + ".o"))));
        mf.d1 = toNonPrimitiveByteArray(Files.readAllBytes(Paths.get((fileName + ".s1"))));
        mf.d2 = toNonPrimitiveByteArray(Files.readAllBytes(Paths.get((fileName + ".s2"))));
        byte[] zFile = Files.readAllBytes(Paths.get((fileName + ".z")));

        mf.redundantBits = zFile[0];
        mf.escapeLength = zFile[1];
        System.arraycopy(zFile, 2, mf.bv, 0, zFile.length - 2);

        return mf;
    }

    static MWCAFile readSingleStream(String fileName) throws IOException {
        MWCAFile mf = new MWCAFile();
        byte[] input = Files.readAllBytes(Paths.get(fileName));

        mf.redundantBits = input[0];
        mf.escapeLength = input[1];

        mf.s1 = new Byte[ByteBuffer.wrap(input,2,4).getInt()];
        mf.s2 = new Byte[ByteBuffer.wrap(input, 6,4).getInt()];
        mf.bv = new Byte[ByteBuffer.wrap(input, 10,4).getInt()];
        mf.s3 = new Byte[ByteBuffer.wrap(input, 14,4).getInt()];
        mf.d1 = new Byte[ByteBuffer.wrap(input,18,4).getInt()];
        mf.d2 = new Byte[ByteBuffer.wrap(input,22,4).getInt()];

        int totalLength = 26;
        Byte[] inputNonPrim = toNonPrimitiveByteArray(input);
        System.arraycopy(inputNonPrim, totalLength, mf.s1, 0, mf.s1.length);
        totalLength += mf.s1.length;

        System.arraycopy(inputNonPrim, totalLength, mf.s2, 0, mf.s2.length);
        totalLength += mf.s2.length;

        System.arraycopy(inputNonPrim, totalLength, mf.bv, 0, mf.bv.length);
        totalLength += mf.bv.length;

        System.arraycopy(inputNonPrim, totalLength, mf.s3, 0, mf.s3.length);
        totalLength += mf.s3.length;

        System.arraycopy(inputNonPrim, totalLength, mf.d1, 0, mf.d1.length);
        totalLength += mf.d1.length;

        System.arraycopy(inputNonPrim, totalLength, mf.d2, 0, mf.d2.length);
        totalLength += mf.d2.length;

        return mf;
    }

    static Byte[] toByteArray(Boolean[] input) {
        byte[] buffer = new byte[input.length / 8];
        for (int entry = 0; entry < buffer.length; entry++)
            for (int bit = 0; bit < 8; bit++)
                if (input[entry * 8 + bit])
                    buffer[entry] |= (128 >> bit);

        Byte[] bytes = new Byte[buffer.length];
        for (int i=0;i<buffer.length;i++)
            bytes[i] = buffer[i];
        return bytes;
    }

    static Boolean[] toBoolArray(Byte[] input){
        Boolean[] bits = new Boolean[input.length * 8];
        for (int i = 0; i < input.length * 8; i++)
            if ((input[i / 8] & (1 << (7 - (i % 8)))) > 0)
                bits[i] = true;
        return bits;
    }

    private static String[] sortAndTakeElements(HashMap<String, Integer> unsortMap, final boolean order,int elementCount) {
        List<Map.Entry<String, Integer>> list = new LinkedList<>(unsortMap.entrySet());
        Collections.sort(list, (o1, o2) -> {
            if (order)
                return o1.getValue().compareTo(o2.getValue());
            else
                return o2.getValue().compareTo(o1.getValue());
        });
        elementCount = Math.min(elementCount,unsortMap.size());
        String[] dictionaryArray = new String[elementCount];
        for(int i=0;i<elementCount;i++)
            dictionaryArray[i]=list.get(i).getKey();
        return dictionaryArray;
    }

    private static byte[] toPrimitiveByteArray(Byte[] array){
        byte[] primitive = new byte[array.length];
        for(int i=0;i< array.length;i++)
            primitive[i]=array[i];
        return primitive;
    }

    private static Byte[] toNonPrimitiveByteArray(byte[] array) {
            Byte[] nonPrimitive = new Byte[array.length];
            for(int i=0;i< array.length;i++)
                nonPrimitive[i]=array[i];
            return nonPrimitive;
    }
}
