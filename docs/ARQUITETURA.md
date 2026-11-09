# Arquitetura do Rebrotar

Visão geral das partes do sistema e das decisões por trás delas.

```
 Celular Android
 ┌──────────────────────────────────────────────────────────┐
 │  App (React Native, Expo, TypeScript)                    │
 │   telas, plano de limites, diário, jardim, áudios        │
 │        │                         ▲                       │
 │        ▼                         │ eventos de bloqueio   │
 │  Módulo nativo (Kotlin)          │                       │
 │   Serviço de Acessibilidade ─────┘                       │
 │   BlockerRules (regras puras, testadas no computador)    │
 │   Tela de bloqueio nativa, notificações, uso por app     │
 │                                                          │
 │  Armazenamento local cifrado (diário, hábitos, plano)    │
 └───────────────┬──────────────────────────────────────────┘
                 │ só mensagens da conversa com a Lia
                 ▼
 ┌──────────────────────────────┐        ┌───────────────────┐
 │ Servidor da Lia (Node.js)    │───────▶│ Modelo de IA      │
 │ Google Cloud Run             │        │ (Gemini)          │
 │ limites por IP, sem logs de  │        └───────────────────┘
 │ conversa, crise tratada antes│
 └──────────────────────────────┘
```

## 1. App

React Native com Expo e Expo Router. Cada tela é uma rota. O estado vive em um armazenamento local com cópia de segurança: se a leitura falhar, o app não sobrescreve dados ilegíveis, tenta de novo e grava ao sair.

**Contagem única de dias.** Há um número só para todos os hábitos. Uma recaída ou três dias sem regar a planta zeram a contagem. Quem volta continua do estágio atual da planta.

## 2. Bloqueio nativo

O bloqueio de sites e o limite de tempo precisam rodar com o app fechado. Por isso usam um módulo Kotlin com um Serviço de Acessibilidade.

- **Só bloqueia ao confirmar.** Enquanto a pessoa digita na barra de endereço, o serviço não bloqueia nada, para o autocompletar do navegador não disparar um bloqueio por engano.
- **Fecha a aba bloqueada.** Foi a correção de um bug real: o navegador guardava a aba aberta e, ao voltar, o bloqueio disparava de novo e a pessoa nunca mais conseguia usar o navegador.
- **Não lê o que não é página.** Visão geral de abas, histórico, configurações e endereços internos nunca são lidos nem bloqueados.
- **Orçamento de ações.** Ações com efeito colateral (como abrir abas) têm limite por janela de tempo. O robô não consegue inundar o navegador, aconteça o que acontecer.
- **Caminho de reserva.** Se a tela por cima não puder ser desenhada, volta ao caminho antigo (tela do app mais notificação).

As decisões ficam em `BlockerRules`, sem nada do Android, justamente para serem testadas no computador.

## 3. Lia

O app envia a conversa a um servidor Node.js no Google Cloud Run, que monta as instruções da Lia e chama o modelo de IA.

- **Crise:** mensagens com sinais de risco recebem uma resposta fixa, sem passar pela IA, com indicação do CVV (188).
- **Honestidade:** a Lia diz que é uma IA e não psicóloga.
- **Proteções do servidor:** limite de mensagens por IP, histórico e tamanho de mensagem limitados e logs sem o conteúdo da conversa.
- **Sem internet:** o app tem respostas de reserva.

## 4. Privacidade e segurança

- Sem cadastro, sem e-mail e sem senha.
- Dados sensíveis (diário, hábitos, recaídas e dívidas) cifrados no aparelho. Os dados da tela de bloqueio nativa ficam cifrados com AES-256-GCM usando o Android Keystore.
- "Apagar meus dados" remove tudo: chave, foto, fila de bloqueios e lembretes.
- Permissões mínimas. Câmera, microfone e armazenamento são bloqueados no manifesto.
- Chaves e credenciais ficam fora do código-fonte.

## 5. Lançamento

Build de release assinado, teste interno na Google Play, vídeo da declaração de acessibilidade, política de privacidade, declarações de segurança de dados e revisão da loja. Cada versão passou pelo teste interno antes de ir para produção.

## Limitações conhecidas

- O código ofuscado (R8) ainda não está ativo no build de release.
- O bloqueio depende de como cada navegador expõe suas telas, e foi testado em um conjunto limitado de aparelhos.
- Um navegador que ignore a aba neutra pode fazer o bloqueio reabrir.
