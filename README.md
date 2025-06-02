
# Reputação com Blockchain

**Disciplina**: FGA0053 - Sistemas Multiagentes <br>
**Nro do Grupo**: 03<br>
**Frente de Pesquisa**: Reputação<br>

## Alunos
|Matrícula | Aluno |
| -- | -- |
| 22/2006884  |  JOSÉ VINICIUS ALVARES SOARES DE QUEIROZ |
| 22/1038248  |  IGOR ALVES DE ABREU |
| 25/1035022 |   CAMILE BARBOSA GONZAGA DE OLIVEIRA |
| 23/1011909  |  GUILHERME NASCIMENTO TEGNOUE |
| 22/1038140  |  VITOR EVANGELISTA DA SILVA ALVES |

## Sobre 
O objetivo do grupo é desenvolver uma simulação inovadora da bolsa de valores brasileira empregando sistemas multiagentes. O projeto inovou integrando blockchain para assegurar a integridade das transações.Cada agente representa um participante do mercado e o comportamento desses agentes é um elemento central do nosso trabalho, eles tomarão decisões de investimento e negociação baseadas em um sistema de reputação. Isso significa que as interações e o desempenho passado de cada agente influenciarão suas estratégias futuras, criando um modelo realista e adaptativo do mercado financeiro.

## Instalação 
**Linguagens**: Java<br>
**Tecnologias**: Eclipse, VScode,Maven e Jade.<br>
**pré-requisitos**: Ter instalado o Java Development Kit (JDK), a Framework JADE e um gerenciador de dependências, a equipe usou o Maven. Configurações como adicionar os JARs do JADE ao CLASSPATH e declarar as dependências do JADE no seu arquivo de configuração. Utilizar uma boa IDE é opcional, mas Recomendado.

## Uso 
Cumprido os pré-requisitos,abrimos em uma IDE a pasta raiz do projeto. A IDE deve reconhecer a estrutura de um projeto Java e carregar as configurações(isso acontecerá se seu pacote JDK estiver devidamente instalado). Localize o documento `Main.java`: Este é o centro da simulação pois contém o código para iniciar o contêiner principal do JADE e instanciar os agentes (Broker e Investor). Projetos Java, para serem executados precisam de instruções específicas para a JVM(Maquína Virtual do Java). Essas instruções são argumentos e a maneiroa como você adiciona esses argumentos depende da iDE utilizada. Em Eclipse, você pode configurar "Run Configurations" para adicionar esses argumentos JVM. Em VS Code, isso pode ser feito no arquivo launch.json. Projetos JADE requerem argumentos específicos na linha de comando para configurar o ambiente do agente (por exemplo, -gui para iniciar a interface gráfica do JADE, ou -agents para especificar os agentes a serem criados no início). Um exemplo do projeto em questão seria "-gui -agents Broker:MinhaClasseAgente1;Investor:MinhaClasseAgente2;Stock:MinhaClasseAgente3;". Uma vez rodando, a simulação deve exibir logs no terminal ou, uma interface gráfica que mostra os agentes, suas mensagens e o estado do mercado, no caso do JADE GUI. Observe o comportamento dos agentes Broker e Investor e como suas decisões (baseadas em "reputação") afetam o mercado de ações simulado. Caso queira modificar o comportamento da simulação ou adicionar componentes edite os arquivos .java dos agentes e `Main.java` para ajustar os parâmetros iniciais da simulação.


## Vídeo
---

## Participações
|Nome do Membro | Contribuição | Significância da Contribuição para o Projeto (Excelente/Boa/Regular/Ruim/Nula) | Comprobatórios (Branchs)
| -- | -- | -- | -- |
| José  |  Programação dos agentes Broker,Investor e Stock;Configuração dos ambientes e integração geral do projeto | Boa | Main
| Igor  |  Programação do algoritmo de reputação dos agentes e pesquisa bibliográfica necessária ao projeto| Boa | Igor
| Camile  |  Programação de uma branch paralela de auxílio,do agente Banco Central e documentação | Boa | Camile
| Guilherme  |  Programação dos Fatos da Base de Conhecimento Lógica | Boa | devel
| Vitor  |  Programação do algoritmo de blockchain gerenciada por agentes | Boa | Vitor

## Outros 
Este projeto é uma simulação do mercado de ações brasileiro, desenvolvida para explorar a interação de sistemas multiagentes (SMA) em um ambiente dinâmico, onde o comportamento dos agentes (corretores e investidores) é influenciado por um sistema de reputação. A tecnologia blockchain é empregada para garantir a integridade e imutabilidade das transações, proporcionando insights sobre estratégias de negociação e a emergência de padrões complexos no mercado.O projeto inclui o código-fonte principal em src/StockMarket/ (`Main.java`, `Broker.java`, `Investor.java`, `Stock.java`), arquivos de configuração de projeto como .classpath e .project, e um diretório bin/ para os arquivos compilados.
É importante ressaltar a verificação da versão de cadaprograma pois tem um impacto significante na realização do trabalho. A principal lição foi a complexidade da aplicação de um SMA, interação multiagente e como o comportamento baseado em reputação gera dinâmicas emergentes. Contribuímos com um modelo híbrido inovador  para omercado financeiro. As fragilidades incluem desafios de escalabilidade para um grande número de agentes, a simplificação do modelo de reputação e a representação básica do mercado.

## Trabalhos Futuros 
Planejamos otimizar a performance, implementar modelos de comportamento de agentes mais complexos, expandir o modelo de mercado com mais fatores reais, desenvolver uma visualização aprimorada, integrar contratos inteligentes e aprimorar a análise de dados pós-simulação.

## Fontes
- CORRẼA DA SILVA,André. Guia de instalação JADE(Jade Agente DEvelopment) no sistema operacional Linux.2024.9. Projeto de pesquisa – Universidade 
- INSTITUTO DE CIÊNCIA E TECNOLOGIA (Itália). JADE: Java Agent Development Framework. [S. l.]: Telecom Italia Labs, [s.d.]. Disponível em: http://jade.tilab.com/. Acesso em: 2 jun. 2025.de Brasília, Faculdade do Gama, 2025.
-B3 S.A. – BRASIL, BOLSA, BALCÃO. [S. l.]: B3, [s.d.]. Disponível em: http://www.b3.com.br. Acesso em: 2 jun. 2025.

