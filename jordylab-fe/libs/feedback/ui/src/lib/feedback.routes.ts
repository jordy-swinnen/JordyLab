import { Route } from '@angular/router';
import { FeedbackPageComponent } from './feedback-page/feedback-page.component';
import { InboxPageComponent } from './inbox-page/inbox-page.component';

/** The guest tab, mounted by the host under `/settings/feedback`. */
export const guestFeedbackRoutes: Route[] = [{ path: '', component: FeedbackPageComponent }];

/** The admin tab, mounted by the host under `/settings/inbox`. */
export const adminInboxRoutes: Route[] = [{ path: '', component: InboxPageComponent }];
