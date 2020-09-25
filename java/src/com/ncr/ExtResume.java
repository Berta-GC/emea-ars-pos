package com.ncr;

import org.apache.log4j.Logger;

import java.io.*;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;

/**
 * AMZ-2017#ADD -- entire module
 */
public class ExtResume extends Action {

    // AMZ-2017-002#BEG
    private static final Logger logger = Logger.getLogger(ExtResume.class);
    private final static String extTenderPropertiesFilePath = "conf/extendedTender.properties";
    private final static HashMap<String, Integer> extTenderMap = new HashMap<String, Integer>();
    // AMZ-2017-002#END
    public static boolean enabled = false;
    public static boolean supervisor = false;
    private final static String flagFilePath = "extres.dat";
    private final static String logFilePath = "log/extres.log";

    //DMA-FIX_EXTRESUME#A BEG

    public static void fileMove(String source, String dest) {
        logger.info("ENTER");
        logger.info("moving file >" + source + "< in >" + dest + "<");
        InputStream inStream = null;
        OutputStream outStream = null;

        try {

            File afile = new File(source);
            File bfile = new File(dest);

            inStream = new FileInputStream(afile);
            outStream = new FileOutputStream(bfile);

            byte[] buffer = new byte[1024];

            int length;
            //copy the file content in bytes
            while ((length = inStream.read(buffer)) > 0) {
                outStream.write(buffer, 0, length);
            }


            inStream.close();
            outStream.close();

            //delete the original file

            String fileSource = afile.getPath();
            if (afile.delete())
                logger.info(fileSource + " deleted successful!");
            else
                logger.info(fileSource + " NOT DELETED!");

            System.out.println("File is copied successful!");
            logger.info("File is copied successful!");

        } catch (Exception e) {
            logger.info("File move failed... e: " + e);
        }
        logger.info("EXIT");
    }
    public static void cleanTRA(){

        String terminator = "\r\n";
        RandomAccessFile stra_file;
        File straPathfile = localFile("data", "S_TRA" + REG + ".DAT");

        RandomAccessFile stra_tmp;
        File straTmpPathfile = localFile("data", "S_TRA" + REG + ".TMP");
        String line;
        String pattern = STO + ":" + REG + ":";
        try {
            logger.info("Reading " + straPathfile.getName());
            stra_file = new RandomAccessFile(straPathfile, "r");
            stra_tmp = new RandomAccessFile(straTmpPathfile, "rw");
            while((line = stra_file.readLine()) != null){
                logger.info("line len:" + line.length() );
                logger.info("line: >" + line + "< ");
                if (line.length() == 78 && line.startsWith(pattern)){
                    logger.info("ok");
                    stra_tmp.writeBytes(line+terminator);
                    continue;
                }else if (line.length() > 78){
                    try {
                        long position = stra_file.getFilePointer() - line.length() - terminator.length();
                        int start = line.indexOf(pattern);
                        String cleanedLine = line.substring(start, start + 78);
                        logger.info("cleanedLine: >" + cleanedLine + "< ");
                        stra_tmp.writeBytes(cleanedLine + terminator);
                    }catch (Exception e){
                        logger.info("e: " + e.getMessage());
                        continue;
                    }
                }else{
                    logger.info("line too short : " + line.length());
                }
            }
            stra_file.close();
            stra_tmp.close();
            fileMove(straTmpPathfile.getAbsolutePath(), straPathfile.getAbsolutePath());
        }catch (Exception e){
            logger.info("exception in open file : " + straPathfile.getName());
        }
    }

    //DMA-FIX_EXTRESUME#A END
    public static void consolidateTra() {
        int rec;
        int nbr = 0;

        ExtResume.cleanTRA(); //DMA-FIX_EXTRESUME#A
        lTRA.open("data", "S_TRA" + REG + ".DAT", 1);
        if (lTRA.getSize() > 0) {
            lTRA.read(1);
            writeLogFile("");
            writeLogFile("------------------------");
            Date today = new Date();
            writeLogFile(today + "");
            writeLogFile("Found broken transaction");
            String traNbr = "" + lTRA.pb.charAt(23) + lTRA.pb.charAt(24) + lTRA.pb.charAt(25) + lTRA.pb.charAt(26);
            writeLogFile("Number = " + traNbr);
            String ckrNbr = "" + lTRA.pb.charAt(38) + lTRA.pb.charAt(39) + lTRA.pb.charAt(40) + lTRA.pb.charAt(41);
            writeLogFile("Checker = " + ckrNbr);
            writeLogFile("------------------------");

            ctl.setDatim(); // Setta date e time

            if (Integer.parseInt(traNbr) != ctl.tran) {
                writeLogFile("Warning : cmos transaction number = " + ctl.tran + " differs from broken transaction number = " + traNbr);
                writeLogFile("Cannnot resume such transaction");
                for (rec = 0; lTRA.read(++rec) > 0; ) {
                    writeLogFile(lTRA.pb);
                }
                lTRA.close();
                return;
            }

            Itmdc.IDC_write('F', trx_pres(), tra.spf3, "", tra.cnt, tra.amt);

            writeFlagFile(editHex(ctl.reg_nbr, 3) + editNum(ctl.tran, 4));

            for (rec = 0; lTRA.read(++rec) > 0; ) {
                char type = lTRA.pb.charAt(32);

                if (type == 'C') /* skip empl/cust % template */
                    if (lTRA.pb.charAt(35) == '9')
                        continue;
                // EMEA-UPB-DMA#BEG
                if (type == 'u') {
                    String ean = lTRA.pb.substring(43, 59);
                    // logger.info("yyyy: " + lTRA.pb);
                    // logger.info("xxxx: >" + ean+"<");
                    int i = WinUpb.getInstance().findUpbTra(ean, false);
                    if (i >= 0 && tra.itemsVsUPB.get(i).isConfirmed()) {
                        lTRA.pb = lTRA.pb.substring(0, lTRA.pb.length() - 1) + "0";
                    }
                }
                // EMEA-UPB-DMA#END
                lIDC.onto(0, lTRA.scan(28)).push(editNum(++nbr, 3));
                lIDC.push(lTRA.skip(3).scan(3)).push("8");
                lIDC.push(lTRA.pb.substring(++lTRA.index));
                if (type == 'F')
                    if (ctl.alert)
                        lIDC.poke(38, '*');
                lIDC.write();
            }
            posWrite(1, 1, 0);  // Aumenta di 1 il numero di transazione
        }
        lTRA.close();
    }


    public static void writeFlagFile(String text) {
        try {
            File file = new File(flagFilePath);
            if (file.exists()) {
                file.delete();
            }
            file.createNewFile();
            FileWriter fw = new FileWriter(file.getAbsoluteFile());
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(text);
            bw.close();
            fw.close();
        } catch (Exception e) {
        }
    }

    /**
     * @return null se il flagFile e' vuoto o non esiste altrimenti la stringa in flagFile (extres.dat)
     */
    public static String readFlagFile() {
        String ret = null;
        if (!enabled) {
            return null;
        }
        try {
            File file = new File(flagFilePath);
            if (file.exists()) {
                FileReader fr = new FileReader(file.getAbsoluteFile());
                BufferedReader br = new BufferedReader(fr);
                ret = br.readLine();
                br.close();
                fr.close();
            }
        } catch (Exception e) {
        }
        return ret;
    }

    public static void writeLogFile(String text) {
        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(logFilePath, true)));
            out.println(text);
            out.close();
        } catch (Exception e) {
        }
    }

    // AMZ-2017-002#BEG

    public static void readExtendedTender() {
        logger.debug("ENTER");
        Properties extTenderParam = new Properties();
        try {
            extTenderParam.load(new FileInputStream(extTenderPropertiesFilePath));
            for (String s : extTenderParam.stringPropertyNames()) {
                String name = extTenderParam.getProperty(s);
                int tnd = Integer.parseInt(s.split("\\.")[1]);
                if (name.contains(";")) {
                    String[] tokens = name.split(";");
                    for (String token : tokens) {
                        extTenderMap.put(token, tnd);
                        logger.info("param " + s + " : tender = " + tnd + ", value = " + token);
                    }
                } else {
                    extTenderMap.put(name, tnd);
                    logger.info("param " + s + " : tender = " + tnd + ", value = " + name);
                }
            }
            logger.info("test getExtTenderNumber(\"AMZ-TEST-23\") = " + getExtTenderNumber("AMZ-TEST-23", 100));
            logger.info("test getExtTenderNumber(\"xxx\") = " + getExtTenderNumber("xxx", 100));
        } catch (Exception e) {
            logger.error("Exception: ", e);
        }
        logger.debug("EXIT");
    }

    public static int getExtTenderNumber(String tenderName, int defaultValue) {
        logger.debug("ENTER");
        Integer tnd;
        if (tenderName != null) {
            tnd = extTenderMap.get(tenderName);
            if (tnd == null) {
                logger.warn("Cannot find tender name = " + tenderName);
                logger.warn("Returning tender default = " + defaultValue);
                tnd = defaultValue;
            }
        } else {
            logger.warn("Cannot find tender name = null");
            logger.warn("Returning tender default = " + defaultValue);
            tnd = defaultValue;
        }
        logger.debug("EXIT tender returned = " + tnd);
        return tnd;
    }

    // AMZ-2017-002#END

}
