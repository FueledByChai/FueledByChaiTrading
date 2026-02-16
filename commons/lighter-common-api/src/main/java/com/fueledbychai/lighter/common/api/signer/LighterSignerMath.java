package com.fueledbychai.lighter.common.api.signer;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class LighterSignerMath {

    private static final BigInteger GOLDILOCKS_MODULUS = new BigInteger("18446744069414584321");
    private static final BigInteger GOLDILOCKS_MODULUS_MINUS_ONE = GOLDILOCKS_MODULUS.subtract(BigInteger.ONE);
    private static final BigInteger GOLDILOCKS_MODULUS_MINUS_ONE_DIV_TWO = GOLDILOCKS_MODULUS_MINUS_ONE.shiftRight(1);
    private static final BigInteger ECGFP5_SCALAR_ORDER = new BigInteger(
            "1067993516717146951041484916571792702745057740581727230159139685185762082554198619328292418486241");

    private static final int POSEIDON_WIDTH = 12;
    private static final int POSEIDON_RATE = 8;
    private static final int POSEIDON_ROUNDS_F_HALF = 4;
    private static final int POSEIDON_ROUNDS_P = 22;

    private static final GoldilocksField[][] POSEIDON_EXTERNAL_CONSTANTS = initExternalConstants();
    private static final GoldilocksField[] POSEIDON_INTERNAL_CONSTANTS = initInternalConstants();
    private static final GoldilocksField[] POSEIDON_MATRIX_DIAG = initMatrixDiag();

    private static final Fp5 FP5_ZERO = new Fp5(
            GoldilocksField.ZERO,
            GoldilocksField.ZERO,
            GoldilocksField.ZERO,
            GoldilocksField.ZERO,
            GoldilocksField.ZERO);
    private static final Fp5 FP5_ONE = new Fp5(
            GoldilocksField.ONE,
            GoldilocksField.ZERO,
            GoldilocksField.ZERO,
            GoldilocksField.ZERO,
            GoldilocksField.ZERO);
    private static final Fp5 FP5_TWO = new Fp5(
            GoldilocksField.fromSignedLong(2),
            GoldilocksField.ZERO,
            GoldilocksField.ZERO,
            GoldilocksField.ZERO,
            GoldilocksField.ZERO);
    private static final GoldilocksField FP5_W = GoldilocksField.fromSignedLong(3);
    private static final GoldilocksField FP5_DTH_ROOT = GoldilocksField.fromUnsignedDecimal("1041288259238279555");

    private static final Fp5 A_ECGFP5_POINT = Fp5.fromUnsignedLong(2L);
    private static final Fp5 B_ECGFP5_POINT = new Fp5(
            GoldilocksField.ZERO,
            GoldilocksField.fromSignedLong(263),
            GoldilocksField.ZERO,
            GoldilocksField.ZERO,
            GoldilocksField.ZERO);
    private static final Fp5 B_MUL2_ECGFP5_POINT = B_ECGFP5_POINT.scalarMul(GoldilocksField.fromSignedLong(2));
    private static final Fp5 B_MUL4_ECGFP5_POINT = B_ECGFP5_POINT.scalarMul(GoldilocksField.fromSignedLong(4));

    private static final Fp5 A_WEIERSTRASS = new Fp5(
            GoldilocksField.fromUnsignedDecimal("6148914689804861439"),
            GoldilocksField.fromSignedLong(263),
            GoldilocksField.ZERO,
            GoldilocksField.ZERO,
            GoldilocksField.ZERO);
    private static final Fp5 A_ECGFP5_DIV_THREE = A_ECGFP5_POINT.div(Fp5.fromUnsignedLong(3L));

    private static final WeierstrassPoint GENERATOR_WEIERSTRASS = new WeierstrassPoint(
            new Fp5(
                    GoldilocksField.fromUnsignedDecimal("11712523173042564207"),
                    GoldilocksField.fromUnsignedDecimal("14090224426659529053"),
                    GoldilocksField.fromUnsignedDecimal("13197813503519687414"),
                    GoldilocksField.fromUnsignedDecimal("16280770174934269299"),
                    GoldilocksField.fromUnsignedDecimal("15998333998318935536")),
            new Fp5(
                    GoldilocksField.fromUnsignedDecimal("14639054205878357578"),
                    GoldilocksField.fromUnsignedDecimal("17426078571020221072"),
                    GoldilocksField.fromUnsignedDecimal("2548978194165003307"),
                    GoldilocksField.fromUnsignedDecimal("8663895577921260088"),
                    GoldilocksField.fromUnsignedDecimal("9793640284382595140")),
            false);
    private static final WeierstrassPoint WEIERSTRASS_NEUTRAL = new WeierstrassPoint(FP5_ZERO, FP5_ZERO, true);

    private LighterSignerMath() {
    }

    static Scalar parsePrivateKeyHex(String privateKeyHex) {
        byte[] privateKeyBytes = decodeHex(privateKeyHex);
        if (privateKeyBytes.length != 40) {
            throw new IllegalArgumentException(
                    "Invalid Lighter private key length. Expected 40 bytes but got " + privateKeyBytes.length
                            + " bytes.");
        }
        return Scalar.fromLittleEndian(privateKeyBytes);
    }

    static Scalar sampleScalar(SecureRandom random) {
        BigInteger sampled = new BigInteger(ECGFP5_SCALAR_ORDER.bitLength(), random).mod(ECGFP5_SCALAR_ORDER);
        return Scalar.fromBigInteger(sampled);
    }

    static Fp5 publicKeyFromPrivateKey(Scalar privateKey) {
        if (privateKey == null) {
            throw new IllegalArgumentException("privateKey is required");
        }
        return GENERATOR_WEIERSTRASS.multiply(privateKey).encode();
    }

    static byte[] decodeHex(String hexValue) {
        if (hexValue == null) {
            throw new IllegalArgumentException("hexValue is required");
        }
        String normalized = hexValue.trim();
        if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
        }
        if (normalized.length() % 2 != 0) {
            normalized = "0" + normalized;
        }
        if (normalized.isEmpty()) {
            return new byte[0];
        }

        int length = normalized.length();
        byte[] output = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            int hi = Character.digit(normalized.charAt(i), 16);
            int lo = Character.digit(normalized.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("Invalid hex value: " + hexValue);
            }
            output[i / 2] = (byte) ((hi << 4) | lo);
        }
        return output;
    }

    static String toHex(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >>> 4) & 0x0F, 16));
            builder.append(Character.forDigit(value & 0x0F, 16));
        }
        return builder.toString();
    }

    static Fp5 hashToQuinticExtension(List<GoldilocksField> input) {
        List<GoldilocksField> output = hashNToMNoPad(input, 5);
        return new Fp5(output.get(0), output.get(1), output.get(2), output.get(3), output.get(4));
    }

    static Fp5 hashAuthTokenMessage(String message) {
        if (message == null) {
            throw new IllegalArgumentException("message is required");
        }
        List<GoldilocksField> messageElements = GoldilocksField.fromCanonicalLittleEndianBytes(
                message.getBytes(StandardCharsets.UTF_8));
        return hashToQuinticExtension(messageElements);
    }

    static SchnorrSignature signHashedMessage(Fp5 hashedMessage, Scalar privateKey, SecureRandom random) {
        Scalar nonce = sampleScalar(random);
        return signHashedMessage(hashedMessage, privateKey, nonce);
    }

    static SchnorrSignature signHashedMessage(Fp5 hashedMessage, Scalar privateKey, Scalar nonce) {
        Fp5 encodedR = GENERATOR_WEIERSTRASS.multiply(nonce).encode();

        List<GoldilocksField> preimage = new ArrayList<>(10);
        preimage.addAll(Arrays.asList(encodedR.limbs));
        preimage.addAll(Arrays.asList(hashedMessage.limbs));

        Scalar e = Scalar.fromFp5(hashToQuinticExtension(preimage));
        Scalar s = nonce.sub(e.mul(privateKey));
        return new SchnorrSignature(s, e);
    }

    private static List<GoldilocksField> hashNToMNoPad(List<GoldilocksField> input, int numOutputs) {
        GoldilocksField[] perm = new GoldilocksField[POSEIDON_WIDTH];
        Arrays.fill(perm, GoldilocksField.ZERO);

        for (int i = 0; i < input.size(); i += POSEIDON_RATE) {
            for (int j = 0; j < POSEIDON_RATE && i + j < input.size(); j++) {
                perm[j] = input.get(i + j);
            }
            permute(perm);
        }

        List<GoldilocksField> outputs = new ArrayList<>(numOutputs);
        while (true) {
            for (int i = 0; i < POSEIDON_RATE; i++) {
                outputs.add(perm[i]);
                if (outputs.size() == numOutputs) {
                    return outputs;
                }
            }
            permute(perm);
        }
    }

    private static void permute(GoldilocksField[] state) {
        externalLinearLayer(state);
        fullRounds(state, 0);
        partialRounds(state);
        fullRounds(state, POSEIDON_ROUNDS_F_HALF);
    }

    private static void fullRounds(GoldilocksField[] state, int startRound) {
        for (int round = startRound; round < startRound + POSEIDON_ROUNDS_F_HALF; round++) {
            addRoundConstants(state, round);
            applySBox(state);
            externalLinearLayer(state);
        }
    }

    private static void partialRounds(GoldilocksField[] state) {
        for (int round = 0; round < POSEIDON_ROUNDS_P; round++) {
            state[0] = state[0].add(POSEIDON_INTERNAL_CONSTANTS[round]);
            state[0] = sbox(state[0]);
            internalLinearLayer(state);
        }
    }

    private static void addRoundConstants(GoldilocksField[] state, int round) {
        for (int i = 0; i < POSEIDON_WIDTH; i++) {
            state[i] = state[i].add(POSEIDON_EXTERNAL_CONSTANTS[round][i]);
        }
    }

    private static void applySBox(GoldilocksField[] state) {
        for (int i = 0; i < state.length; i++) {
            state[i] = sbox(state[i]);
        }
    }

    private static GoldilocksField sbox(GoldilocksField value) {
        GoldilocksField valueSquared = value.square();
        GoldilocksField valueCubed = valueSquared.mul(value);
        GoldilocksField valueSixth = valueCubed.square();
        return valueSixth.mul(value);
    }

    private static void externalLinearLayer(GoldilocksField[] state) {
        for (int i = 0; i < 3; i++) {
            int offset = i * 4;

            GoldilocksField t0 = state[offset].add(state[offset + 1]);
            GoldilocksField t1 = state[offset + 2].add(state[offset + 3]);
            GoldilocksField t2 = t0.add(t1);
            GoldilocksField t3 = t2.add(state[offset + 1]);
            GoldilocksField t4 = t2.add(state[offset + 3]);
            GoldilocksField t5 = state[offset].doubleValue();
            GoldilocksField t6 = state[offset + 2].doubleValue();

            state[offset] = t3.add(t0);
            state[offset + 1] = t6.add(t3);
            state[offset + 2] = t1.add(t4);
            state[offset + 3] = t5.add(t4);
        }

        GoldilocksField[] sums = new GoldilocksField[4];
        Arrays.fill(sums, GoldilocksField.ZERO);
        for (int k = 0; k < 4; k++) {
            for (int j = 0; j < POSEIDON_WIDTH; j += 4) {
                sums[k] = sums[k].add(state[j + k]);
            }
        }

        for (int i = 0; i < POSEIDON_WIDTH; i++) {
            state[i] = state[i].add(sums[i % 4]);
        }
    }

    private static void internalLinearLayer(GoldilocksField[] state) {
        GoldilocksField sum = GoldilocksField.ZERO;
        for (GoldilocksField value : state) {
            sum = sum.add(value);
        }

        for (int i = 0; i < POSEIDON_WIDTH; i++) {
            state[i] = state[i].mul(POSEIDON_MATRIX_DIAG[i]).add(sum);
        }
    }

    private static GoldilocksField[][] initExternalConstants() {
        String[][] raw = {
                { "15492826721047263190", "11728330187201910315", "8836021247773420868", "16777404051263952451",
                        "5510875212538051896", "6173089941271892285", "2927757366422211339", "10340958981325008808",
                        "8541987352684552425", "9739599543776434497", "15073950188101532019", "12084856431752384512" },
                { "4584713381960671270", "8807052963476652830", "54136601502601741", "4872702333905478703",
                        "5551030319979516287", "12889366755535460989", "16329242193178844328", "412018088475211848",
                        "10505784623379650541", "9758812378619434837", "7421979329386275117", "375240370024755551" },
                { "3331431125640721931", "15684937309956309981", "578521833432107983", "14379242000670861838",
                        "17922409828154900976", "8153494278429192257", "15904673920630731971", "11217863998460634216",
                        "3301540195510742136", "9937973023749922003", "3059102938155026419", "1895288289490976132" },
                { "5580912693628927540", "10064804080494788323", "9582481583369602410", "10186259561546797986",
                        "247426333829703916", "13193193905461376067", "6386232593701758044", "17954717245501896472",
                        "1531720443376282699", "2455761864255501970", "11234429217864304495", "4746959618548874102" },
                { "13571697342473846203", "17477857865056504753", "15963032953523553760", "16033593225279635898",
                        "14252634232868282405", "8219748254835277737", "7459165569491914711", "15855939513193752003",
                        "16788866461340278896", "7102224659693946577", "3024718005636976471", "13695468978618890430" },
                { "8214202050877825436", "2670727992739346204", "16259532062589659211", "11869922396257088411",
                        "3179482916972760137", "13525476046633427808", "3217337278042947412", "14494689598654046340",
                        "15837379330312175383", "8029037639801151344", "2153456285263517937", "8301106462311849241" },
                { "13294194396455217955", "17394768489610594315", "12847609130464867455", "14015739446356528640",
                        "5879251655839607853", "9747000124977436185", "8950393546890284269", "10765765936405694368",
                        "14695323910334139959", "16366254691123000864", "15292774414889043182", "10910394433429313384" },
                { "17253424460214596184", "3442854447664030446", "3005570425335613727", "10859158614900201063",
                        "9763230642109343539", "6647722546511515039", "909012944955815706", "18101204076790399111",
                        "11588128829349125809", "15863878496612806566", "5201119062417750399", "176665553780565743" }
        };

        GoldilocksField[][] constants = new GoldilocksField[raw.length][raw[0].length];
        for (int i = 0; i < raw.length; i++) {
            for (int j = 0; j < raw[i].length; j++) {
                constants[i][j] = GoldilocksField.fromUnsignedDecimal(raw[i][j]);
            }
        }
        return constants;
    }

    private static GoldilocksField[] initInternalConstants() {
        String[] raw = {
                "11921381764981422944", "10318423381711320787", "8291411502347000766", "229948027109387563",
                "9152521390190983261", "7129306032690285515", "15395989607365232011", "8641397269074305925",
                "17256848792241043600", "6046475228902245682", "12041608676381094092", "12785542378683951657",
                "14546032085337914034", "3304199118235116851", "16499627707072547655", "10386478025625759321",
                "13475579315436919170", "16042710511297532028", "1411266850385657080", "9024840976168649958",
                "14047056970978379368", "838728605080212101" };

        GoldilocksField[] constants = new GoldilocksField[raw.length];
        for (int i = 0; i < raw.length; i++) {
            constants[i] = GoldilocksField.fromUnsignedDecimal(raw[i]);
        }
        return constants;
    }

    private static GoldilocksField[] initMatrixDiag() {
        String[] rawHex = {
                "c3b6c08e23ba9300", "d84b5de94a324fb6", "0d0c371c5b35b84f", "7964f570e7188037",
                "5daf18bbd996604b", "6743bc47b9595257", "5528b9362c59bb70", "ac45e25b7127b68b",
                "a2077d7dfbb606b5", "f3faac6faee378ae", "0c6388b51545e883", "d27dbb6944917b60" };

        GoldilocksField[] matrixDiag = new GoldilocksField[rawHex.length];
        for (int i = 0; i < rawHex.length; i++) {
            matrixDiag[i] = GoldilocksField.fromUnsignedHex(rawHex[i]);
        }
        return matrixDiag;
    }

    static final class GoldilocksField {

        private static final GoldilocksField ZERO = new GoldilocksField(BigInteger.ZERO);
        private static final GoldilocksField ONE = new GoldilocksField(BigInteger.ONE);

        private final BigInteger value;

        private GoldilocksField(BigInteger value) {
            this.value = normalize(value, GOLDILOCKS_MODULUS);
        }

        static GoldilocksField fromUnsignedDecimal(String decimalValue) {
            return new GoldilocksField(new BigInteger(decimalValue));
        }

        static GoldilocksField fromUnsignedHex(String hexValue) {
            return new GoldilocksField(new BigInteger(hexValue, 16));
        }

        static GoldilocksField fromUnsignedLong(long unsignedValue) {
            return new GoldilocksField(toUnsignedBigInteger(unsignedValue));
        }

        static GoldilocksField fromSignedLong(long signedValue) {
            return new GoldilocksField(BigInteger.valueOf(signedValue));
        }

        GoldilocksField add(GoldilocksField other) {
            return new GoldilocksField(value.add(other.value));
        }

        GoldilocksField sub(GoldilocksField other) {
            return new GoldilocksField(value.subtract(other.value));
        }

        GoldilocksField mul(GoldilocksField other) {
            return new GoldilocksField(value.multiply(other.value));
        }

        GoldilocksField square() {
            return mul(this);
        }

        GoldilocksField doubleValue() {
            return add(this);
        }

        GoldilocksField negate() {
            if (isZero()) {
                return ZERO;
            }
            return new GoldilocksField(GOLDILOCKS_MODULUS.subtract(value));
        }

        GoldilocksField inverse() {
            if (isZero()) {
                throw new IllegalStateException("inverse of zero");
            }
            return new GoldilocksField(value.modInverse(GOLDILOCKS_MODULUS));
        }

        GoldilocksField pow(BigInteger exponent) {
            return new GoldilocksField(value.modPow(exponent, GOLDILOCKS_MODULUS));
        }

        boolean isZero() {
            return value.signum() == 0;
        }

        boolean isEven() {
            return !value.testBit(0);
        }

        BigInteger asBigInteger() {
            return value;
        }

        byte[] toLittleEndian8() {
            byte[] bigEndian = value.toByteArray();
            byte[] result = new byte[8];
            int copyLength = Math.min(bigEndian.length, 8);
            for (int i = 0; i < copyLength; i++) {
                result[i] = bigEndian[bigEndian.length - 1 - i];
            }
            return result;
        }

        static GoldilocksField sqrtOrNull(GoldilocksField target) {
            if (target.isZero()) {
                return ZERO;
            }

            int legendre = legendreSymbol(target.value);
            if (legendre != 1) {
                return null;
            }

            BigInteger q = GOLDILOCKS_MODULUS_MINUS_ONE;
            int s = 0;
            while (!q.testBit(0)) {
                q = q.shiftRight(1);
                s++;
            }

            BigInteger z = BigInteger.TWO;
            while (legendreSymbol(z) != -1) {
                z = z.add(BigInteger.ONE);
            }

            BigInteger c = z.modPow(q, GOLDILOCKS_MODULUS);
            BigInteger t = target.value.modPow(q, GOLDILOCKS_MODULUS);
            BigInteger r = target.value.modPow(q.add(BigInteger.ONE).shiftRight(1), GOLDILOCKS_MODULUS);

            int m = s;
            while (!t.equals(BigInteger.ONE)) {
                int i = 1;
                BigInteger t2i = t.multiply(t).mod(GOLDILOCKS_MODULUS);
                while (i < m && !t2i.equals(BigInteger.ONE)) {
                    t2i = t2i.multiply(t2i).mod(GOLDILOCKS_MODULUS);
                    i++;
                }

                BigInteger b = c.modPow(BigInteger.ONE.shiftLeft(m - i - 1), GOLDILOCKS_MODULUS);
                r = r.multiply(b).mod(GOLDILOCKS_MODULUS);
                t = t.multiply(b).multiply(b).mod(GOLDILOCKS_MODULUS);
                c = b.multiply(b).mod(GOLDILOCKS_MODULUS);
                m = i;
            }

            return new GoldilocksField(r);
        }

        static List<GoldilocksField> fromCanonicalLittleEndianBytes(byte[] inputBytes) {
            List<GoldilocksField> result = new ArrayList<>();
            if (inputBytes == null || inputBytes.length == 0) {
                return result;
            }

            for (int offset = 0; offset < inputBytes.length; offset += 8) {
                byte[] chunk = new byte[8];
                int remaining = Math.min(8, inputBytes.length - offset);
                System.arraycopy(inputBytes, offset, chunk, 0, remaining);
                result.add(fromLittleEndian8(chunk));
            }

            return result;
        }

        private static GoldilocksField fromLittleEndian8(byte[] littleEndian) {
            if (littleEndian.length != 8) {
                throw new IllegalArgumentException("Expected exactly 8 bytes for a Goldilocks field element.");
            }
            byte[] bigEndian = new byte[8];
            for (int i = 0; i < 8; i++) {
                bigEndian[7 - i] = littleEndian[i];
            }
            return new GoldilocksField(new BigInteger(1, bigEndian));
        }

        private static int legendreSymbol(BigInteger value) {
            BigInteger candidate = normalize(value, GOLDILOCKS_MODULUS);
            if (candidate.signum() == 0) {
                return 0;
            }
            BigInteger ls = candidate.modPow(GOLDILOCKS_MODULUS_MINUS_ONE_DIV_TWO, GOLDILOCKS_MODULUS);
            if (ls.equals(BigInteger.ONE)) {
                return 1;
            }
            if (ls.equals(GOLDILOCKS_MODULUS.subtract(BigInteger.ONE))) {
                return -1;
            }
            return 0;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof GoldilocksField)) {
                return false;
            }
            GoldilocksField that = (GoldilocksField) other;
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }

    static final class Fp5 {

        private static final int LIMBS = 5;

        private final GoldilocksField[] limbs;

        Fp5(GoldilocksField limb0, GoldilocksField limb1, GoldilocksField limb2, GoldilocksField limb3,
                GoldilocksField limb4) {
            this.limbs = new GoldilocksField[] { limb0, limb1, limb2, limb3, limb4 };
        }

        static Fp5 fromUnsignedLong(long value) {
            return new Fp5(
                    GoldilocksField.fromUnsignedLong(value),
                    GoldilocksField.ZERO,
                    GoldilocksField.ZERO,
                    GoldilocksField.ZERO,
                    GoldilocksField.ZERO);
        }

        GoldilocksField[] toLimbArray() {
            return new GoldilocksField[] { limbs[0], limbs[1], limbs[2], limbs[3], limbs[4] };
        }

        byte[] toLittleEndian40() {
            byte[] output = new byte[40];
            for (int i = 0; i < LIMBS; i++) {
                byte[] limbBytes = limbs[i].toLittleEndian8();
                System.arraycopy(limbBytes, 0, output, i * 8, 8);
            }
            return output;
        }

        Fp5 add(Fp5 other) {
            return new Fp5(
                    limbs[0].add(other.limbs[0]),
                    limbs[1].add(other.limbs[1]),
                    limbs[2].add(other.limbs[2]),
                    limbs[3].add(other.limbs[3]),
                    limbs[4].add(other.limbs[4]));
        }

        Fp5 sub(Fp5 other) {
            return new Fp5(
                    limbs[0].sub(other.limbs[0]),
                    limbs[1].sub(other.limbs[1]),
                    limbs[2].sub(other.limbs[2]),
                    limbs[3].sub(other.limbs[3]),
                    limbs[4].sub(other.limbs[4]));
        }

        Fp5 negate() {
            return new Fp5(
                    limbs[0].negate(),
                    limbs[1].negate(),
                    limbs[2].negate(),
                    limbs[3].negate(),
                    limbs[4].negate());
        }

        Fp5 doubleValue() {
            return add(this);
        }

        Fp5 triple() {
            GoldilocksField three = GoldilocksField.fromSignedLong(3);
            return scalarMul(three);
        }

        Fp5 scalarMul(GoldilocksField scalar) {
            return new Fp5(
                    limbs[0].mul(scalar),
                    limbs[1].mul(scalar),
                    limbs[2].mul(scalar),
                    limbs[3].mul(scalar),
                    limbs[4].mul(scalar));
        }

        Fp5 mul(Fp5 other) {
            GoldilocksField a0b0 = limbs[0].mul(other.limbs[0]);
            GoldilocksField a1b4 = limbs[1].mul(other.limbs[4]);
            GoldilocksField a2b3 = limbs[2].mul(other.limbs[3]);
            GoldilocksField a3b2 = limbs[3].mul(other.limbs[2]);
            GoldilocksField a4b1 = limbs[4].mul(other.limbs[1]);
            GoldilocksField c0 = a0b0.add(FP5_W.mul(a1b4.add(a2b3).add(a3b2).add(a4b1)));

            GoldilocksField a0b1 = limbs[0].mul(other.limbs[1]);
            GoldilocksField a1b0 = limbs[1].mul(other.limbs[0]);
            GoldilocksField a2b4 = limbs[2].mul(other.limbs[4]);
            GoldilocksField a3b3 = limbs[3].mul(other.limbs[3]);
            GoldilocksField a4b2 = limbs[4].mul(other.limbs[2]);
            GoldilocksField c1 = a0b1.add(a1b0).add(FP5_W.mul(a2b4.add(a3b3).add(a4b2)));

            GoldilocksField a0b2 = limbs[0].mul(other.limbs[2]);
            GoldilocksField a1b1 = limbs[1].mul(other.limbs[1]);
            GoldilocksField a2b0 = limbs[2].mul(other.limbs[0]);
            GoldilocksField a3b4 = limbs[3].mul(other.limbs[4]);
            GoldilocksField a4b3 = limbs[4].mul(other.limbs[3]);
            GoldilocksField c2 = a0b2.add(a1b1).add(a2b0).add(FP5_W.mul(a3b4.add(a4b3)));

            GoldilocksField a0b3 = limbs[0].mul(other.limbs[3]);
            GoldilocksField a1b2 = limbs[1].mul(other.limbs[2]);
            GoldilocksField a2b1 = limbs[2].mul(other.limbs[1]);
            GoldilocksField a3b0 = limbs[3].mul(other.limbs[0]);
            GoldilocksField a4b4 = limbs[4].mul(other.limbs[4]);
            GoldilocksField c3 = a0b3.add(a1b2).add(a2b1).add(a3b0).add(FP5_W.mul(a4b4));

            GoldilocksField a0b4 = limbs[0].mul(other.limbs[4]);
            GoldilocksField a1b3 = limbs[1].mul(other.limbs[3]);
            GoldilocksField a2b2 = limbs[2].mul(other.limbs[2]);
            GoldilocksField a3b1 = limbs[3].mul(other.limbs[1]);
            GoldilocksField a4b0 = limbs[4].mul(other.limbs[0]);
            GoldilocksField c4 = a0b4.add(a1b3).add(a2b2).add(a3b1).add(a4b0);

            return new Fp5(c0, c1, c2, c3, c4);
        }

        Fp5 square() {
            GoldilocksField doubleW = FP5_W.doubleValue();

            GoldilocksField c0 = limbs[0].mul(limbs[0])
                    .add(doubleW.mul(limbs[1].mul(limbs[4]).add(limbs[2].mul(limbs[3]))));

            GoldilocksField c1 = limbs[0].doubleValue().mul(limbs[1])
                    .add(doubleW.mul(limbs[2].mul(limbs[4])))
                    .add(FP5_W.mul(limbs[3].mul(limbs[3])));

            GoldilocksField c2 = limbs[0].doubleValue().mul(limbs[2])
                    .add(limbs[1].mul(limbs[1]))
                    .add(doubleW.mul(limbs[4].mul(limbs[3])));

            GoldilocksField c3 = limbs[0].doubleValue().mul(limbs[3])
                    .add(limbs[1].doubleValue().mul(limbs[2]))
                    .add(FP5_W.mul(limbs[4].mul(limbs[4])));

            GoldilocksField c4 = limbs[0].doubleValue().mul(limbs[4])
                    .add(limbs[1].doubleValue().mul(limbs[3]))
                    .add(limbs[2].mul(limbs[2]));

            return new Fp5(c0, c1, c2, c3, c4);
        }

        Fp5 div(Fp5 divisor) {
            Fp5 inverse = divisor.inverseOrZero();
            if (inverse.isZero()) {
                throw new IllegalStateException("division by zero");
            }
            return mul(inverse);
        }

        Fp5 inverseOrZero() {
            if (isZero()) {
                return FP5_ZERO;
            }

            Fp5 d = frobenius();
            Fp5 e = d.mul(d.frobenius());
            Fp5 f = e.mul(e.repeatedFrobenius(2));

            GoldilocksField gg = limbs[0].mul(f.limbs[0])
                    .add(FP5_W.mul(limbs[1].mul(f.limbs[4])
                            .add(limbs[2].mul(f.limbs[3]))
                            .add(limbs[3].mul(f.limbs[2]))
                            .add(limbs[4].mul(f.limbs[1]))));

            return f.scalarMul(gg.inverse());
        }

        Fp5 frobenius() {
            return repeatedFrobenius(1);
        }

        Fp5 repeatedFrobenius(int count) {
            if (count == 0) {
                return this;
            }
            int normalizedCount = count % LIMBS;
            if (normalizedCount == 0) {
                return this;
            }

            GoldilocksField z0 = FP5_DTH_ROOT;
            for (int i = 1; i < normalizedCount; i++) {
                z0 = z0.mul(FP5_DTH_ROOT);
            }

            GoldilocksField[] powers = powers(z0, LIMBS);
            return new Fp5(
                    limbs[0].mul(powers[0]),
                    limbs[1].mul(powers[1]),
                    limbs[2].mul(powers[2]),
                    limbs[3].mul(powers[3]),
                    limbs[4].mul(powers[4]));
        }

        GoldilocksField legendre() {
            Fp5 frob1 = frobenius();
            Fp5 frob2 = frob1.frobenius();

            Fp5 frob1TimesFrob2 = frob1.mul(frob2);
            Fp5 frob2Frob1TimesFrob2 = frob1TimesFrob2.repeatedFrobenius(2);

            Fp5 xrExt = this.mul(frob1TimesFrob2).mul(frob2Frob1TimesFrob2);
            GoldilocksField xr = xrExt.limbs[0];

            GoldilocksField xr31 = expPowerOfTwo(xr, 31);
            GoldilocksField xr31Inv = xr31.isZero() ? GoldilocksField.ZERO : xr31.inverse();
            GoldilocksField xr63 = expPowerOfTwo(xr31, 32);
            return xr63.mul(xr31Inv);
        }

        Fp5 expPowerOfTwo(int exponent) {
            Fp5 result = this;
            for (int i = 0; i < exponent; i++) {
                result = result.square();
            }
            return result;
        }

        Fp5 canonicalSqrtOrNull() {
            Fp5 sqrt = sqrtOrNull();
            if (sqrt == null) {
                return null;
            }
            return sgn0(sqrt) ? sqrt.negate() : sqrt;
        }

        Fp5 sqrtOrNull() {
            Fp5 v = expPowerOfTwo(31);
            Fp5 d = this.mul(v.expPowerOfTwo(32)).mul(v.inverseOrZero());
            Fp5 e = d.mul(d.repeatedFrobenius(2)).frobenius();
            Fp5 f = e.square();

            GoldilocksField g = limbs[0].mul(f.limbs[0]).add(
                    GoldilocksField.fromSignedLong(3).mul(
                            limbs[1].mul(f.limbs[4])
                                    .add(limbs[2].mul(f.limbs[3]))
                                    .add(limbs[3].mul(f.limbs[2]))
                                    .add(limbs[4].mul(f.limbs[1]))));

            GoldilocksField baseSqrt = GoldilocksField.sqrtOrNull(g);
            if (baseSqrt == null) {
                return null;
            }

            Fp5 eInverse = e.inverseOrZero();
            return Fp5.fromUnsignedLong(0L).withLimb0(baseSqrt).mul(eInverse);
        }

        private Fp5 withLimb0(GoldilocksField newLimb0) {
            return new Fp5(newLimb0, GoldilocksField.ZERO, GoldilocksField.ZERO, GoldilocksField.ZERO,
                    GoldilocksField.ZERO);
        }

        boolean isZero() {
            return limbs[0].isZero() && limbs[1].isZero() && limbs[2].isZero() && limbs[3].isZero() && limbs[4].isZero();
        }

        static Fp5 fromCanonicalLittleEndian40(byte[] input) {
            if (input == null || input.length != 40) {
                throw new IllegalArgumentException("Expected 40 bytes for Fp5 element.");
            }

            GoldilocksField[] resultLimbs = new GoldilocksField[5];
            for (int i = 0; i < 5; i++) {
                byte[] chunk = new byte[8];
                System.arraycopy(input, i * 8, chunk, 0, 8);
                resultLimbs[i] = GoldilocksField.fromLittleEndian8(chunk);
            }
            return new Fp5(resultLimbs[0], resultLimbs[1], resultLimbs[2], resultLimbs[3], resultLimbs[4]);
        }

        private static boolean sgn0(Fp5 value) {
            boolean sign = false;
            boolean zero = true;
            for (GoldilocksField limb : value.limbs) {
                boolean signI = limb.isEven();
                boolean zeroI = limb.isZero();
                sign = sign || (zero && signI);
                zero = zero && zeroI;
            }
            return sign;
        }

        private static GoldilocksField expPowerOfTwo(GoldilocksField value, int exponent) {
            GoldilocksField result = value;
            for (int i = 0; i < exponent; i++) {
                result = result.square();
            }
            return result;
        }

        private static GoldilocksField[] powers(GoldilocksField base, int count) {
            GoldilocksField[] values = new GoldilocksField[count];
            values[0] = GoldilocksField.ONE;
            for (int i = 1; i < count; i++) {
                values[i] = values[i - 1].mul(base);
            }
            return values;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Fp5)) {
                return false;
            }
            Fp5 that = (Fp5) other;
            return Arrays.equals(limbs, that.limbs);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(limbs);
        }
    }

    static final class Scalar {

        private final BigInteger value;

        private Scalar(BigInteger value) {
            this.value = normalize(value, ECGFP5_SCALAR_ORDER);
        }

        static Scalar fromBigInteger(BigInteger value) {
            return new Scalar(value);
        }

        static Scalar fromLittleEndian(byte[] input) {
            if (input == null || input.length != 40) {
                throw new IllegalArgumentException("Expected 40 bytes for ECgFp5 scalar.");
            }
            BigInteger asBigInteger = BigInteger.ZERO;
            for (int i = input.length - 1; i >= 0; i--) {
                asBigInteger = asBigInteger.shiftLeft(8).add(BigInteger.valueOf(input[i] & 0xFFL));
            }
            return new Scalar(asBigInteger);
        }

        static Scalar fromFp5(Fp5 value) {
            BigInteger result = BigInteger.ZERO;
            GoldilocksField[] limbs = value.toLimbArray();
            for (int i = 4; i >= 0; i--) {
                result = result.shiftLeft(64).add(limbs[i].asBigInteger());
            }
            return new Scalar(result);
        }

        Scalar add(Scalar other) {
            return new Scalar(value.add(other.value));
        }

        Scalar sub(Scalar other) {
            return new Scalar(value.subtract(other.value));
        }

        Scalar mul(Scalar other) {
            return new Scalar(value.multiply(other.value));
        }

        byte[] toLittleEndian40() {
            byte[] result = new byte[40];
            BigInteger current = value;
            for (int i = 0; i < result.length; i++) {
                result[i] = current.and(BigInteger.valueOf(0xFFL)).byteValue();
                current = current.shiftRight(8);
            }
            return result;
        }

        BigInteger asBigInteger() {
            return value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Scalar)) {
                return false;
            }
            Scalar that = (Scalar) other;
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    static final class WeierstrassPoint {

        private final Fp5 x;
        private final Fp5 y;
        private final boolean isInfinity;

        WeierstrassPoint(Fp5 x, Fp5 y, boolean isInfinity) {
            this.x = x;
            this.y = y;
            this.isInfinity = isInfinity;
        }

        WeierstrassPoint add(WeierstrassPoint other) {
            if (isInfinity) {
                return other;
            }
            if (other.isInfinity) {
                return this;
            }

            boolean xSame = x.equals(other.x);
            boolean yDifferent = !y.equals(other.y);

            Fp5 lambda0;
            Fp5 lambda1;
            if (xSame) {
                lambda0 = x.square().triple().add(A_WEIERSTRASS);
                lambda1 = y.doubleValue();
            } else {
                lambda0 = other.y.sub(y);
                lambda1 = other.x.sub(x);
            }

            Fp5 lambda = lambda0.div(lambda1);
            Fp5 x3 = lambda.square().sub(x).sub(other.x);
            Fp5 y3 = lambda.mul(x.sub(x3)).sub(y);

            return new WeierstrassPoint(x3, y3, xSame && yDifferent);
        }

        WeierstrassPoint doublePoint() {
            if (isInfinity) {
                return this;
            }

            Fp5 lambda0 = x.square().triple().add(A_WEIERSTRASS);
            Fp5 lambda1 = y.doubleValue();
            Fp5 lambda = lambda0.div(lambda1);

            Fp5 x2 = lambda.square().sub(x.doubleValue());
            Fp5 y2 = lambda.mul(x.sub(x2)).sub(y);

            return new WeierstrassPoint(x2, y2, false);
        }

        WeierstrassPoint multiply(Scalar scalar) {
            BigInteger k = scalar.asBigInteger();
            if (k.signum() == 0) {
                return WEIERSTRASS_NEUTRAL;
            }

            WeierstrassPoint result = WEIERSTRASS_NEUTRAL;
            WeierstrassPoint addend = this;

            int bits = k.bitLength();
            for (int i = 0; i < bits; i++) {
                if (k.testBit(i)) {
                    result = result.add(addend);
                }
                addend = addend.doublePoint();
            }
            return result;
        }

        Fp5 encode() {
            return y.div(A_ECGFP5_DIV_THREE.sub(x));
        }
    }

    static final class SchnorrSignature {

        private final Scalar s;
        private final Scalar e;

        SchnorrSignature(Scalar s, Scalar e) {
            this.s = s;
            this.e = e;
        }

        byte[] toBytes() {
            byte[] sBytes = s.toLittleEndian40();
            byte[] eBytes = e.toLittleEndian40();
            byte[] output = new byte[80];
            System.arraycopy(sBytes, 0, output, 0, 40);
            System.arraycopy(eBytes, 0, output, 40, 40);
            return output;
        }

        Scalar getS() {
            return s;
        }

        Scalar getE() {
            return e;
        }
    }

    private static BigInteger normalize(BigInteger value, BigInteger modulus) {
        BigInteger normalized = value.mod(modulus);
        return normalized.signum() < 0 ? normalized.add(modulus) : normalized;
    }

    private static BigInteger toUnsignedBigInteger(long value) {
        byte[] bytes = new byte[8];
        for (int i = 0; i < 8; i++) {
            bytes[7 - i] = (byte) (value >>> (8 * i));
        }
        return new BigInteger(1, bytes);
    }
}
