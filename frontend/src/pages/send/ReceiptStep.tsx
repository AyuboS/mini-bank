import { Link } from 'react-router-dom';
import type { PayeeLookupResponse, TransferResponse } from '../../api/types';
import { Button } from '../../components/ui';
import { formatDateTime, formatMoney, maskAccountNumber } from '../../utils/format';

interface ReceiptStepProps {
  transfer: TransferResponse;
  recipient: PayeeLookupResponse;
  onSendAnother: () => void;
}

/** Step 4: what the server recorded, with the reference to quote if something is wrong. */
export function ReceiptStep({ transfer, recipient, onSendAnother }: ReceiptStepProps) {
  const held = transfer.status === 'PENDING' || transfer.status === 'PROCESSING';
  const failed = transfer.status === 'FAILED';

  return (
    <div className="send-step">
      <div role="status">
        <p className="send-receipt__title">
          {failed ? 'Payment failed' : held ? 'Held for review' : 'Payment sent'}
        </p>
        <p className="send-receipt__note">
          {failed
            ? (transfer.failureReason ?? 'No money has moved.')
            : held
              ? 'No money has moved yet. The payment is being checked and you will be notified when it is released.'
              : `${recipient.holderName} has the money now.`}
        </p>
      </div>

      <dl className="send-summary">
        <div className="send-summary__total">
          <dt>Amount</dt>
          <dd>{formatMoney(transfer.amount, transfer.currency)}</dd>
        </div>
        <div>
          <dt>To</dt>
          <dd>
            {recipient.holderName}
            <span className="send-summary__sub">{transfer.toAccountNumber}</span>
          </dd>
        </div>
        <div>
          <dt>From</dt>
          <dd>{maskAccountNumber(transfer.fromAccountNumber)}</dd>
        </div>
        <div>
          <dt>Date</dt>
          <dd>{formatDateTime(transfer.completedAt ?? transfer.createdAt)}</dd>
        </div>
        <div>
          <dt>Reference</dt>
          <dd className="send-summary__reference">{transfer.reference}</dd>
        </div>
      </dl>

      <div className="send-actions">
        <Link className="btn btn-secondary" to="/transactions">
          View in transactions
        </Link>
        <Button variant="primary" onClick={onSendAnother}>
          Send another
        </Button>
      </div>
    </div>
  );
}
