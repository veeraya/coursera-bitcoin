import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TxHandler {

    private UTXOPool utxoPool;
    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        if (tx.numInputs() <= 0) return false;
        Set<UTXO> utxoSet = new HashSet<>();
        double inputSum = 0.0;
        double outputSum = 0.0;

        for (int i = 0; i < tx.getInputs().size(); i++) {
            Transaction.Input input = tx.getInput(i);
            UTXO utxo = getUTXO(input);
            if (!this.utxoPool.contains(utxo)) {
                return false;
            }
            Transaction.Output spentOutput = utxoPool.getTxOutput(utxo);
            if (!Crypto.verifySignature(spentOutput.address, tx.getRawDataToSign(i),input.signature)){
                return false;
            }
            if (utxoSet.contains(utxo)) {
                return  false;
            }
            inputSum += spentOutput.value;
            utxoSet.add(utxo);
        }

        for (Transaction.Output output : tx.getOutputs()) {
            if (output.value < 0) {
                return false;
            }
            outputSum += output.value;
        }

        return outputSum <= inputSum;
    }

    private boolean checkAlInputInUTXOPool(List<Transaction.Input> inputs) {
        for (Transaction.Input input : inputs) {
            UTXO utxo = getUTXO(input);
            if (!this.utxoPool.contains(utxo)) {
                return false;
            }

        }
        return inputs.size() > 0;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        List<Transaction> validTransactions = new ArrayList<>();
        for (Transaction tx : possibleTxs) {
            if (isValidTx(tx)) {
                // remove spent input
                for (int i = 0; i < tx.numInputs(); i++) {
                    Transaction.Input input = tx.getInput(i);
                    UTXO utxo = getUTXO(input);
                    utxoPool.removeUTXO(utxo);
                }
                // add unspent output
                for (int i = 0; i < tx.numOutputs(); i++ ) {
                    UTXO utxo = new UTXO(tx.getHash(), i);
                    Transaction.Output output = tx.getOutput(i);
                    utxoPool.addUTXO(utxo, output);
                }
                validTransactions.add(tx);
            }
        }
        Transaction[] validTransactionArray = new Transaction[validTransactions.size()];
        return validTransactions.toArray(validTransactionArray);
    }

    private UTXO getUTXO(Transaction.Input input) {
        return new UTXO(input.prevTxHash, input.outputIndex);
    }

}
