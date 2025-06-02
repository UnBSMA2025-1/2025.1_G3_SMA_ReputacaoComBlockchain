#manter na branch do vitor

import hashlib
import json
import os
from datetime import datetime
from typing import List, Dict, Optional, Tuple
from multiprocessing import Process, Queue

class Block:
    def __init__(self, index: int, previous_hash: str, transactions: List[Dict], timestamp: Optional[float] = None): 
        """
        Inicializa um novo bloco na blockchain de investimentos.
        
        Parâmetros:
            index: Posição do bloco na cadeia (0 para genesis)
            previous_hash: Hash do bloco anterior (string hex)
            transactions: Lista de transações de compra/venda de ações
            timestamp: Data/hora de criação (opcional, usa atual se None)
        """
        self.index = index
        self.previous_hash = previous_hash
        self.transactions = transactions
        self.timestamp = timestamp or datetime.now().timestamp()
        self.hash = self.calculate_hash()
        self.capacity = 9  # Número máximo de transações por bloco
        self.is_full = len(transactions) >= self.capacity

    def calculate_hash(self) -> str:
        #Calcula o hash SHA-256 do bloco usando seus dados principais
        
        block_string = json.dumps({
            "index": self.index,
            "previous_hash": self.previous_hash,
            "transactions": self.transactions,
            "timestamp": self.timestamp
        }, sort_keys=True).encode()
        return hashlib.sha256(block_string).hexdigest()

    def add_transaction(self, transaction: Dict) -> bool:
        """
        Adiciona uma transação ao bloco se houver capacidade.
        
        Parâmetros:
            transaction: Dicionário com dados da transação de ações
        
        Retorna:
            True se a transação foi adicionada, False se o bloco está cheio
        """
        if self.is_full:
            return False
        
        self.transactions.append(transaction)
        if len(self.transactions) >= self.capacity:
            self.is_full = True
        self.hash = self.calculate_hash()  # Atualiza hash com nova transação
        return True

    def to_dict(self) -> Dict:
        """Converte o bloco para um dicionário serializável."""
        return {
            "index": self.index,
            "previous_hash": self.previous_hash,
            "hash": self.hash,
            "transactions": self.transactions,
            "timestamp": self.timestamp,
            "is_full": self.is_full
        }

class Blockchain:
    def __init__(self, storage_file: str = "blockchain_data.json"):
        """
        Inicializa a blockchain carregando dados do arquivo se existir.
        
        Parâmetros:
            storage_file: Caminho do arquivo para persistência dos dados
        """
        self.storage_file = storage_file
        self.chain: List[Block] = []
        self.load_chain()
    
    def load_chain(self):
        """Carrega a blockchain do arquivo de armazenamento ou cria uma nova."""
        if os.path.exists(self.storage_file):
            try:
                with open(self.storage_file, 'r') as f:
                    chain_data = json.load(f)
                    self.chain = [
                        Block(
                            index=block['index'],
                            previous_hash=block['previous_hash'],
                            transactions=block['transactions'],
                            timestamp=block['timestamp']
                        )
                        for block in chain_data
                    ]
                print(f"Blockchain carregada do arquivo {self.storage_file}")
            except Exception as e:
                print(f"Erro ao carregar blockchain: {e}. Criando nova blockchain.")
                self.chain = [self.create_genesis_block()]
        else:
            self.chain = [self.create_genesis_block()]
            self.save_chain()
    
    def save_chain(self):
        """Salva a blockchain atual no arquivo de armazenamento."""
        try:
            with open(self.storage_file, 'w') as f:
                json.dump([block.to_dict() for block in self.chain], f, indent=2)
        except Exception as e:
            print(f"Erro ao salvar blockchain: {e}")
    
    def create_genesis_block(self) -> Block:
        """Cria o bloco genesis (primeiro bloco da cadeia)."""
        return Block(0, "0", [{
            "type": "genesis",
            "message": "Bloco inicial da blockchain de investimentos",
            "timestamp": datetime.now().timestamp()
        }])
    
    def get_last_block(self) -> Block:
        """Retorna o último bloco da cadeia."""
        return self.chain[-1]
    
    def add_new_block(self, transactions: List[Dict]) -> Block:
        """
        Cria e adiciona um novo bloco à cadeia com as transações fornecidas.
        
        Parâmetros:
            transactions: Lista de transações para incluir no novo bloco
        
        Retorna:
            O novo bloco criado
        """
        last_block = self.get_last_block()
        new_block = Block(len(self.chain), last_block.hash, transactions)
        self.chain.append(new_block)
        self.save_chain()  # Persiste a alteração
        return new_block
    
    def add_transaction(self, transaction: Dict) -> bool:
        """
        Adiciona uma transação ao último bloco ou cria um novo se necessário.
        
        Parâmetros:
            transaction: Dicionário com os dados da transação
        
        Retorna:
            True se a transação foi adicionada com sucesso
        """
        # Validação básica da transação
        required_fields = ['transaction_id', 'investor_id', 'broker_id', 'stock_symbol', 'quantity', 'price', 'type']
        if not all(field in transaction for field in required_fields):
            raise ValueError("Transação inválida: campos obrigatórios faltando")
        
        last_block = self.get_last_block()
        
        if not last_block.is_full:
            success = last_block.add_transaction(transaction)
            if success:
                self.save_chain()  # Persiste a alteração
            return success
        else:
            self.add_new_block([transaction])
            return True
    
    def check_transaction_exists(self, transaction_id: str) -> bool:
        """
        Verifica se uma transação com o ID fornecido existe na blockchain.
        
        Parâmetros:
            transaction_id: ID único da transação
        
        Retorna:
            True se a transação existe, False caso contrário
        """
        for block in self.chain:
            for tx in block.transactions:
                if tx.get('transaction_id') == transaction_id:
                    return True
        return False
    
    def get_investor_transactions(self, investor_id: str) -> List[Dict]:
        """
        Retorna todas as transações de um determinado investidor.
        
        Parâmetros:
            investor_id: ID do investidor
        
        Retorna:
            Lista de transações do investidor ordenadas por timestamp
        """
        transactions = []
        for block in self.chain:
            for tx in block.transactions:
                if tx.get('investor_id') == investor_id:
                    transactions.append(tx)
        
        # Ordena por timestamp (mais recente primeiro)
        return sorted(transactions, key=lambda x: x.get('timestamp', 0), reverse=True)
    
    def get_stock_transactions(self, stock_symbol: str) -> List[Dict]:
        """
        Retorna todas as transações de uma determinada ação.
        
        Parâmetros:
            stock_symbol: Símbolo da ação (ex: 'PETR4')
        
        Retorna:
            Lista de transações da ação ordenadas por timestamp
        """
        transactions = []
        for block in self.chain:
            for tx in block.transactions:
                if tx.get('stock_symbol') == stock_symbol:
                    transactions.append(tx)
        
        # Ordena por timestamp (mais recente primeiro)
        return sorted(transactions, key=lambda x: x.get('timestamp', 0), reverse=True)
    
    def is_chain_valid(self) -> bool:
        """Verifica a integridade da blockchain validando os hashes e links."""
        for i in range(1, len(self.chain)):
            current_block = self.chain[i]
            previous_block = self.chain[i-1]
            
            # Verifica se o hash atual está correto
            if current_block.hash != current_block.calculate_hash():
                return False
            
            # Verifica se o hash anterior está correto
            if current_block.previous_hash != previous_block.hash:
                return False
        
        return True
    
    def to_dict_list(self) -> List[Dict]:
        """Converte toda a blockchain para uma lista de dicionários."""
        return [block.to_dict() for block in self.chain]

class BlockchainAgent(Process):#inherits from "process"
    def __init__(self, command_queue: Queue, response_queue: Queue, storage_file: str = "blockchain_data.json"):
        """
        Agente que gerencia a blockchain e processa comandos assincronamente.
        
        Parâmetros:
            command_queue: Fila para receber comandos de outros agentes
            response_queue: Fila para enviar respostas
            storage_file: Arquivo para armazdenar dados lacais 
        """
        super().__init__()
        self.blockchain = Blockchain(storage_file)
        self.command_queue = command_queue
        self.response_queue = response_queue
        self.storage_file = storage_file
    
    def run(self):
        #main loop do agente para os comandos
        print(f"BlockchainAgent iniciado com arquivo de armazenamento: {self.storage_file}")
        
        #espera "command[0]" pra saber o q fazer, loop acaba no "SHUTDOWN" e tem uma erxception se der erro
        while True:
            try:
                command = self.command_queue.get()
                
                if command[0] == "ADD_TRANSACTION":
                    transaction = command[1]
                    try:
                        success = self.blockchain.add_transaction(transaction)
                        self.response_queue.put(("ADD_TRANSACTION_RESPONSE", success))
                    except ValueError as e:
                        self.response_queue.put(("ERROR", str(e)))
                
                elif command[0] == "CHECK_TRANSACTION":
                    transaction_id = command[1]
                    exists = self.blockchain.check_transaction_exists(transaction_id)
                    self.response_queue.put(("CHECK_TRANSACTION_RESPONSE", exists))
                
                elif command[0] == "GET_INVESTOR_TRANSACTIONS":
                    investor_id = command[1]
                    transactions = self.blockchain.get_investor_transactions(investor_id)
                    self.response_queue.put(("INVESTOR_TRANSACTIONS", transactions))
                
                elif command[0] == "GET_STOCK_TRANSACTIONS":
                    stock_symbol = command[1]
                    transactions = self.blockchain.get_stock_transactions(stock_symbol)
                    self.response_queue.put(("STOCK_TRANSACTIONS", transactions))
                
                elif command[0] == "GET_CHAIN":
                    chain_data = self.blockchain.to_dict_list()
                    self.response_queue.put(("CHAIN_DATA", chain_data))
                
                elif command[0] == "VALIDATE_CHAIN":
                    is_valid = self.blockchain.is_chain_valid()
                    self.response_queue.put(("CHAIN_VALIDATION", is_valid))
                
                elif command[0] == "SHUTDOWN":
                    print("BlockchainAgent encerrando...")
                    break
                
            except Exception as e:
                print(f"Erro no BlockchainAgent: {e}")
                self.response_queue.put(("ERROR", str(e)))

class BlockchainClient:
    def __init__(self, command_queue: Queue, response_queue: Queue):
        """
        Cliente para interagir com o BlockchainAgent pra testar enquanto não implementa na Main
        
        Parâmetros:
            command_queue: Fila para enviar comandos ao agente
            response_queue: Fila para receber respostas do agente
        """
        self.command_queue = command_queue
        self.response_queue = response_queue
    
    def add_transaction(self, transaction_data: Dict) -> Tuple[bool, str]:
        """
        Registra uma nova transação na blockchain.
        
        Parâmetros:
            transaction_data: Dados da transação incluindo:
                - transaction_id: ID único
                - investor_id: ID do investidor
                - broker_id: ID da corretora
                - stock_symbol: Símbolo da ação
                - quantity: Quantidade de ações
                - price: Preço por ação
                - type: 'buy' ou 'sell'
                - timestamp: Data/hora (opcional)
        
        Retorna:
            Tupla (success, message) indicando sucesso e mensagem de erro (se houver)
        """
        self.command_queue.put(("ADD_TRANSACTION", transaction_data))
        response = self.response_queue.get()
        if response[0] == "ERROR":
            return False, response[1]
        return response[1], "Transação adicionada com sucesso"
    
    def check_transaction_exists(self, transaction_id: str) -> bool:
        """
        Verifica se uma transação existe na blockchain
        
        Parâmetros:
            transaction_id: ID da transação
        
        Retorna:
            True se a transação existe, False caso contrário
        """
        self.command_queue.put(("CHECK_TRANSACTION", transaction_id))
        response = self.response_queue.get()
        return response[1]
    
    def get_investor_transactions(self, investor_id: str) -> List[Dict]:
        """
        Obtém todas as transações de um investidor ordenadas por data (mais recente primeiro)
        
        Parâmetros:
            investor_id: ID do investidor
        
        Retorna:
            Lista de transações do investidor
        """
        self.command_queue.put(("GET_INVESTOR_TRANSACTIONS", investor_id))
        response = self.response_queue.get()
        return response[1]
    
    def get_stock_transactions(self, stock_symbol: str) -> List[Dict]:
        """
        Obtém todas as transações de uma ação específica ordenadas por data.
        
        Parâmetros:
            stock_symbol: Símbolo da ação (ex: 'PETR4')
        
        Retorna:
            Lista de transações da ação
        """
        self.command_queue.put(("GET_STOCK_TRANSACTIONS", stock_symbol))
        response = self.response_queue.get()
        return response[1]
    
    def get_full_chain(self) -> List[Dict]:
        """
        Obtém toda a blockchain para inspeção.
        
        Retorna:
            Lista de todos os blocos com suas transações
        """
        self.command_queue.put(("GET_CHAIN",))
        response = self.response_queue.get()
        return response[1]
    
    def validate_chain(self) -> bool:
        """
        Verifica a integridade da blockchain.
        
        Retorna:
            True se a blockchain é válida, False se corrompida
        """
        self.command_queue.put(("VALIDATE_CHAIN",))
        response = self.response_queue.get()
        return response[1]

def create_sample_transaction(investor_id: str, broker_id: str, stock_symbol: str, 
                            quantity: int, price: float, transaction_type: str) -> Dict:
    """
    Cria um dicionário de transação com valores padrão.
    
    Parâmetros:
        investor_id: ID do investidor
        broker_id: ID da corretora
        stock_symbol: Símbolo da ação
        quantity: Quantidade de ações
        price: Preço unitário
        transaction_type: 'buy' ou 'sell'
    
    Retorna:
        Dicionário com dados da transação formatados
    """
    return {
        "transaction_id": f"tx_{datetime.now().timestamp()}",
        "investor_id": investor_id,
        "broker_id": broker_id,
        "stock_symbol": stock_symbol,
        "quantity": quantity,
        "price": price,
        "type": transaction_type,
        "timestamp": datetime.now().timestamp()
    }

def main():
    # Configuração inicial
    blockchain_file = "investments_blockchain.json"
    
    # Criar filas de comunicação
    command_queue = Queue()
    response_queue = Queue()
    
    # Iniciar o agente da blockchain com persistência em arquivo
    blockchain_agent = BlockchainAgent(command_queue, response_queue, blockchain_file)
    blockchain_agent.start()
    
    # Criar cliente para interagir com a blockchain
    client = BlockchainClient(command_queue, response_queue)
    
    # Verificar se a blockchain está válida
    print("\nValidando integridade da blockchain...")
    is_valid = client.validate_chain()
    print(f"Blockchain válida: {'Sim' if is_valid else 'Não'}")
    
    # Adicionar algumas transações de exemplo
    print("\nAdicionando transações de exemplo...")
    
    # Transação 1: Investidor 1001 compra PETR4 via Corretora 2001
    tx1 = create_sample_transaction("inv1001", "bro2001", "PETR4", 150, 32.75, "buy")
    success, message = client.add_transaction(tx1)
    print(f"Transação {tx1['transaction_id']}: {message}")
    
    # Transação 2: Investidor 1002 vende VALE3 via Corretora 2002
    tx2 = create_sample_transaction("inv1002", "bro2002", "VALE3", 80, 67.20, "sell")
    success, message = client.add_transaction(tx2)
    print(f"Transação {tx2['transaction_id']}: {message}")
    
    # Transação 3: Investidor 1001 compra ITUB4 via Corretora 2001
    tx3 = create_sample_transaction("inv1001", "bro2001", "ITUB4", 200, 22.90, "buy")
    success, message = client.add_transaction(tx3)
    print(f"Transação {tx3['transaction_id']}: {message}")
    
    # Consultar transações de um investidor
    print("\nTransações do Investidor inv1001:")
    investor_txs = client.get_investor_transactions("inv1001")
    for tx in investor_txs:
        date = datetime.fromtimestamp(tx['timestamp']).strftime('%d/%m/%Y %H:%M')
        print(f"- {date}: {tx['stock_symbol']} {tx['type']} {tx['quantity']}x R${tx['price']}")
    
    # Consultar transações de uma ação
    print("\nTransações da ação VALE3:")
    stock_txs = client.get_stock_transactions("VALE3")
    for tx in stock_txs:
        date = datetime.fromtimestamp(tx['timestamp']).strftime('%d/%m/%Y %H:%M')
        print(f"- {date}: {tx['investor_id']} {tx['type']} {tx['quantity']}x R${tx['price']}")
    
    # Verificar existência de transação
    print("\nVerificando transações existentes:")
    print(f"Transação {tx1['transaction_id']} existe:", client.check_transaction_exists(tx1['transaction_id']))
    print(f"Transação 'tx_inexistente' existe:", client.check_transaction_exists("tx_inexistente"))
    
    # Encerrar o agente
    command_queue.put(("SHUTDOWN",))
    blockchain_agent.join()
    
    print("\nBlockchain foi persistida no arquivo:", os.path.abspath(blockchain_file))

if __name__ == "__main__":
    main()